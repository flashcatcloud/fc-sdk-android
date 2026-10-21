/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.model.VitalAppLaunchEvent
import com.datadog.android.rum.model.VitalOperationStepEvent
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue

/**
 * FLASHCAT FORK - holds the events of a session kept only in case it reports an error (see
 * `RumConfiguration.Builder.setSessionOnError`), and lets them through once it does.
 *
 * Nothing of such a session reaches the batch until then, and if it never errors nothing ever does:
 * when it ends, what was held is thrown away. Once it errors, the last minute leading up to the
 * error is released in one go and the session carries on like any collected one.
 *
 * Events are held as the batch would have received them - after the event mappers, serialized - so
 * an error a mapper drops releases nothing (a session billed for an error nobody can find would be
 * worse than none), and the memory budget is the size actually uploaded. Events of a WebView in
 * the session are held alongside, as the web SDK sent them - after its own `beforeSend` - see
 * [writeWebEvent].
 *
 * Every RUM event of a collected session passes through here, so that the events of a session
 * whose buffer was thrown away are thrown away too when they arrive late - see [discardedSessionIds].
 * What belongs to no withheld session goes straight to [delegate].
 *
 * All calls but [isReleased] run on the storage thread, in the order they were submitted, which is
 * what keeps a session's control calls in step with its own writes.
 */
internal class WithheldEventWriter(
    private val delegate: RumDataWriter,
    private val internalLogger: InternalLogger,
    private val elapsedTimeNs: () -> Long,
    /**
     * Runs the release after the given delay, on the storage thread, with a writer for the tracking
     * consent in force then. Should consent be withdrawn first, see [ScheduledRelease.runNow]: `drop`
     * is what becomes of a history that may not go out.
     */
    private val scheduleRelease: (
        delayMs: Long,
        release: (EventBatchWriter) -> Unit,
        drop: () -> Unit
    ) -> ScheduledRelease,
    /**
     * How many replay records a view has sent - and, with `includeHeld`, still holds - so released
     * events can claim the replay that goes out with them.
     */
    private val replayRecordsCount: (viewId: String, includeHeld: Boolean) -> Long,
    /**
     * Tells Session Replay the session's events are released, so the replay it holds for that
     * session goes out too - never ahead of the events it attaches to.
     */
    private val releaseReplay: (sessionId: String) -> Unit,
    /**
     * Tells Session Replay the session ended without an error, so the replay it holds for that
     * session goes too. Session Replay keeps a replay whose events are withheld until it hears one
     * or the other: the next session can announce itself before this one's fate is settled here.
     */
    private val discardReplay: (sessionId: String) -> Unit,
    /**
     * Tells Session Replay the session reported its error, the moment it is seen: the replay goes
     * out when the events do, but a stop that comes first must still know to write it.
     */
    private val expectReplayRelease: (sessionId: String) -> Unit
) : DataWriter<Any> {

    /** A release waiting for its jitter. */
    internal fun interface ScheduledRelease {
        /**
         * Consent was withdrawn: the release runs now, through a writer taken when it was scheduled,
         * if consent was granted then. Otherwise the history was never consented to and is dropped:
         * a pending consent withdrawn means its data is wiped, not sent.
         */
        fun runNow()
    }

    private class HeldView(val viewId: String, val date: Long, val event: RawBatchEvent, val eventType: EventType)

    private class HeldEvent(
        val viewId: String?,
        val event: RawBatchEvent,
        val eventType: EventType,
        val heldAtNs: Long,
        val tier: EvictionTier,
        /** False for a crash larger than the whole budget: held apart from it, see [holdDetail]. */
        val budgeted: Boolean = true
    )

    /** What goes first when the buffer is over budget. */
    private enum class EvictionTier {
        /** Long tasks, and requests that succeeded without complaint. */
        FIRST,

        /** Actions, vitals and failed requests: they explain what the user was doing. */
        LAST,

        /**
         * Errors are the reason the session is kept at all, so they go only once nothing else is
         * left - newest first, because the earliest error is the one the session is about.
         */
        LAST_RESORT,

        /**
         * Never evicted: a crash - the process is going down, the history is released with it at
         * once, and it is what that release exists to deliver.
         */
        KEPT
    }

    private var withheldForSessionId: String? = null

    /**
     * A collected session whose replay only is kept on error: nothing of it is held, but its first
     * error still has to be told apart, after the mappers, like a withheld session's.
     */
    private var watchedSessionId: String? = null

    /**
     * The session that earned its release, by an error or by being forced. Marked the moment the
     * error is seen, ahead of the release itself, so a session that ends while its release still
     * waits for the jitter is released rather than thrown away.
     */
    private var releasedSessionId: String? = null

    /** The session whose replay was told to go out: its events have actually been released. */
    private var replayReleasedSessionId: String? = null

    /** A session released by a crash, whose replay waits to see whether the process survived. */
    private var crashReleasedSessionId: String? = null

    /** Latest event per view, least recently updated first. */
    private val views = LinkedHashMap<String, HeldView>()
    private val details = ArrayList<HeldEvent>()
    private var bytes = 0L
    private var droppedCount = 0
    private var currentViewId: String? = null
    private var currentViewDate = Long.MIN_VALUE

    /** When the release was scheduled, which is what freezes the window - see [prune]. */
    private var releaseScheduledAtNs: Long? = null

    private var scheduledRelease: ScheduledRelease? = null

    /**
     * Set once consent is withdrawn while the release still waits for the jitter: that release goes
     * with the consent the history was collected under, and nothing collected after it joins.
     */
    private var holdingClosed = false

    /** Bumped whenever the buffer is cleared, so a release scheduled for its previous content is inert. */
    private var generation = 0

    /**
     * The sessions whose buffers were thrown away. A request that completes after its session ended
     * still carries that session's id, and letting it through would store the very session the
     * withholding avoided.
     */
    private val discardedSessionIds = ArrayDeque<String>()

    // region DataWriter

    @WorkerThread
    override fun write(writer: EventBatchWriter, element: Any, eventType: EventType): Boolean {
        val sessionId = sessionIdOf(element) ?: return delegate.write(writer, element, eventType)
        synchronized(this) {
            if (sessionId == crashReleasedSessionId && element !is ViewEvent) {
                // The process outlived the crash that released this session: its replay goes now.
                // Not on a view: the crash itself updates its view right after the error.
                crashReleasedSessionId = null
                notifyReplayReleased(sessionId)
            }
            if (sessionId in discardedSessionIds) {
                // Accepted and dropped, as the batch would have done had the session never been
                // collected.
                return true
            }
            if (sessionId == watchedSessionId && element is ErrorEvent) {
                // The error the console opens the replay from claims it, if its view has records -
                // only those already written when it is a crash: the held ones die with the process.
                val includeHeld = element.error.isCrash != true
                val claimed = if (replayRecordsCount(element.view.id, includeHeld) > 0) {
                    element.copy(session = element.session.copy(hasReplay = true))
                } else {
                    element
                }
                val written = delegate.write(writer, claimed, eventType)
                // Only the replay is told: [releasedSessionId] belongs to the withheld session,
                // which may be the one running now while this error drains from a stopped one. Not
                // on a crash: the error did not claim the held replay, so none may be written.
                if (written && includeHeld && replayReleasedSessionId != sessionId) {
                    notifyReplayReleased(sessionId)
                }
                return written
            }
            if (sessionId != withheldForSessionId || holdingClosed) {
                // A view of a session whose replay was released claims it as soon as the records
                // exist, held or sent: a final view assembled before they were written would
                // otherwise take the place of the released one that claimed it.
                val claimed = if (
                    sessionId == replayReleasedSessionId && element is ViewEvent &&
                    element.session.hasReplay != true && replayRecordsCount(element.view.id, true) > 0
                ) {
                    element.copy(session = element.session.copy(hasReplay = true))
                } else {
                    element
                }
                return delegate.write(writer, claimed, eventType)
            }
            val batchEvent = delegate.serialize(element) ?: return false
            if (element is ViewEvent) {
                holdView(element.view.id, element.date, batchEvent, eventType)
                // Written locally like any view the batch takes, and only locally: a native crash
                // is reported at the next launch from this file, and is exactly the error such a
                // session is kept for.
                delegate.onDataWritten(element, batchEvent.data)
                return true
            }
            val isCrash = element is ErrorEvent && element.error.isCrash == true
            // Not for a crash: its release is synchronous and lets no replay out, so Session Replay
            // must not take the replay as due should the process survive.
            if (element is ErrorEvent) markReleased(sessionId, expectReplay = !isCrash)
            if (element is ErrorEvent && !isCrash && batchEvent.data.size > BYTES_LIMIT) {
                return writeOversizedError(writer, sessionId, batchEvent, eventType)
            }
            holdDetail(viewIdOf(element), batchEvent, eventType, evictionTierOf(element), element is ErrorEvent)
            releaseIfEarned(writer, sessionId, isCrash)
            return true
        }
    }

    // endregion

    // region WebView

    /**
     * An event of a WebView in a withheld session, already mapped onto the session. Held with the
     * session's own events and released with them, and an error raised in the WebView releases the
     * session like a native one. The web SDK ran its `beforeSend` before handing the event over, so
     * an error that reaches here is one that survived it. Its own errors - `source: agent` - do not
     * count, or every session of an app whose WebView cannot reach the intake would be kept.
     */
    @WorkerThread
    fun writeWebEvent(writer: EventBatchWriter, event: JsonObject) {
        val sessionId = event.stringAt(SESSION_KEY, ID_KEY) ?: return
        synchronized(this) {
            if (sessionId in discardedSessionIds) return
            val batchEvent = RawBatchEvent(data = event.toString().toByteArray(Charsets.UTF_8))
            if (sessionId != withheldForSessionId || holdingClosed) {
                // Released while the event was on its way here: the session is collected now.
                if (sessionId == releasedSessionId) {
                    delegate.writeSerialized(writer, batchEvent, EventType.DEFAULT)
                }
                return
            }
            val type = event.stringAt(TYPE_KEY)
            val viewId = event.stringAt(VIEW_KEY, ID_KEY)
            if (type == WEB_VIEW_TYPE) {
                if (viewId != null) holdView(viewId, event.longAt(DATE_KEY) ?: 0L, batchEvent, EventType.DEFAULT)
                return
            }
            val isError = type == WEB_ERROR_TYPE && event.stringAt(ERROR_KEY, SOURCE_KEY) != WEB_AGENT_SOURCE
            if (isError) markReleased(sessionId, expectReplay = true)
            if (isError && batchEvent.data.size > BYTES_LIMIT) {
                writeOversizedError(writer, sessionId, batchEvent, EventType.DEFAULT)
                return
            }
            holdDetail(viewId, batchEvent, EventType.DEFAULT, webEvictionTierOf(type, event, isError), isError)
            releaseIfEarned(writer, sessionId, isCrash = false)
        }
    }

    /**
     * An error was raised in a WebView of a collected session. Its events go to the batch on their
     * own, but a replay kept on error is released by it like by a native error.
     */
    @WorkerThread
    fun onWebViewError(sessionId: String) {
        synchronized(this) {
            if (sessionId == watchedSessionId && replayReleasedSessionId != sessionId) {
                notifyReplayReleased(sessionId)
            }
        }
    }

    // endregion

    // region Session lifecycle

    /**
     * Set by the session scope, on its own thread, before it queues the first session to withhold
     * or watch. The stop reads it rather than [withheldForSessionId]: that one is set by work that
     * may still be queued, and the core drops queued work once the features are stopped.
     */
    @Volatile
    private var engaged = false

    /** A session is about to be withheld or watched. Called before its work is queued. */
    fun engage() {
        engaged = true
    }

    /**
     * Whether the SDK's stop may have a session to settle. A customer who never turned either
     * switch on never engaged this writer, and pays nothing at the stop.
     */
    fun mayHaveSessionToSettle(): Boolean = engaged

    /** Whether the given session earned its release. Read by the session scope to stop withholding. */
    fun isReleased(sessionId: String): Boolean = synchronized(this) { releasedSessionId == sessionId }

    /**
     * Whether the replay of the given session may go out: its events have actually left - after the
     * jitter for a withheld session, at its first error for a session whose replay only is withheld.
     */
    fun isReplayReleased(sessionId: String): Boolean = synchronized(this) { replayReleasedSessionId == sessionId }

    /**
     * The app went to the background: a release waiting for the jitter goes now, since the process
     * may not live to see the timer. A buffer that has not earned its release stays held - the app
     * often comes straight back, and an error then needs the history before it.
     */
    @WorkerThread
    fun flushScheduledRelease(writer: EventBatchWriter) {
        synchronized(this) {
            if (releaseScheduledAtNs != null) release(writer)
        }
    }

    /**
     * The SDK is stopping: the session ends with it. What a released session still holds goes
     * now, whether or not the jitter has run out; a session that never errored is thrown away,
     * along with the view it wrote locally, so that a crash after the stop is not reported
     * against it at the next launch.
     */
    @WorkerThread
    fun stop(writer: EventBatchWriter) {
        synchronized(this) {
            withheldForSessionId?.let { endSession(it, writer) }
            watchedSessionId?.let { endSession(it, writer) }
        }
    }

    /** Watches a collected session whose replay only is kept on error for its first error. */
    @WorkerThread
    fun watchForError(sessionId: String, writer: EventBatchWriter) {
        synchronized(this) {
            endPreviousSessions(sessionId, writer)
            watchedSessionId = sessionId
        }
    }

    /** Starts withholding the events of a session just drawn as one kept only on error. */
    @WorkerThread
    fun startWithholding(sessionId: String, writer: EventBatchWriter) {
        synchronized(this) {
            endPreviousSessions(sessionId, writer)
            withheldForSessionId = sessionId
        }
    }

    /** A session still held or watched when another one is drawn ended without a word; it ends now. */
    private fun endPreviousSessions(sessionId: String, writer: EventBatchWriter) {
        withheldForSessionId?.takeIf { it != sessionId }?.let { endSession(it, writer) }
        watchedSessionId?.takeIf { it != sessionId }?.let { endSession(it, writer) }
    }

    /**
     * The session ended - expired, renewed or stopped. If it had reported an error, what it holds
     * is released now, whether or not the jitter has run out. If not, it never will, so what it holds
     * is thrown away, and so is anything of it that arrives later. A watched session holds nothing
     * here, but Session Replay holds its replay and waits for the same word.
     */
    @WorkerThread
    fun endSession(sessionId: String, writer: EventBatchWriter) {
        synchronized(this) {
            if (crashReleasedSessionId == sessionId) {
                // Outlived its crash without another event to let the replay out: nothing claims it.
                crashReleasedSessionId = null
                discardReplay(sessionId)
                return
            }
            if (watchedSessionId == sessionId) {
                watchedSessionId = null
                if (replayReleasedSessionId != sessionId) discardReplay(sessionId)
                return
            }
            if (withheldForSessionId != sessionId) return
            if (releasedSessionId == sessionId) {
                release(writer)
            } else {
                discardedSessionIds.addLast(sessionId)
                if (discardedSessionIds.size > DISCARDED_SESSIONS_REMEMBERED) {
                    discardedSessionIds.removeFirstOrNull()
                }
                clear()
                withheldForSessionId = null
                // The last view it wrote locally would otherwise have the native crash reporter
                // attach a crash of a later, uncollected session to it at the next launch.
                delegate.deleteLastViewEvent()
                discardReplay(sessionId)
            }
        }
    }

    /** The host application asked for this session: no reason to wait for an error, nor for the jitter. */
    @WorkerThread
    fun forceRelease(sessionId: String, writer: EventBatchWriter) {
        synchronized(this) {
            if (withheldForSessionId != sessionId) return
            releasedSessionId = sessionId
            release(writer)
        }
    }

    /**
     * Tracking consent was withdrawn while the session was still withholding - see
     * [onConsentWithdrawn]. The session itself goes on, and holds again from scratch should consent
     * be granted back.
     */
    @WorkerThread
    fun dropHeld(sessionId: String) {
        synchronized(this) {
            if (withheldForSessionId != sessionId) return
            onConsentWithdrawn()
        }
    }

    /** Tracking consent was withdrawn - see [onConsentWithdrawn]. */
    @WorkerThread
    fun dropHeldForConsent() {
        synchronized(this) { onConsentWithdrawn() }
    }

    /**
     * A session that already reported its error under granted consent had its minute collected with
     * that consent: it is released now, through a writer taken when its release was scheduled - one
     * taken now would write where consent is now denied - and nothing collected after this joins it.
     * See [ScheduledRelease.runNow] for one scheduled while consent was pending. A session that never
     * errored is thrown away: nothing of it may be kept under the consent now withdrawn.
     */
    private fun onConsentWithdrawn() {
        val sessionId = withheldForSessionId ?: return
        val scheduled = scheduledRelease
        if (releasedSessionId == sessionId && scheduled != null) {
            holdingClosed = true
            scheduled.runNow()
            return
        }
        clear()
        // A session already released holds nothing more after this: its events go to the batch,
        // where consent decides, rather than through here.
        if (releasedSessionId == sessionId) withheldForSessionId = null
    }

    // endregion

    // region Internal

    private fun markReleased(sessionId: String, expectReplay: Boolean) {
        if (releasedSessionId == sessionId) return
        releasedSessionId = sessionId
        if (expectReplay) expectReplayRelease(sessionId)
    }

    /**
     * What was just held may have been the error that earned the release, or come after it. A crash
     * releases at once: the process is going down, the crash handler waits for this write and no
     * timer would ever fire - but the replay held in memory goes down with the process. Otherwise
     * the release waits for its jitter.
     */
    private fun releaseIfEarned(writer: EventBatchWriter, sessionId: String, isCrash: Boolean) {
        if (releasedSessionId != sessionId) return
        if (isCrash) release(writer, replayGoesOut = false) else scheduleReleaseOnce(sessionId)
    }

    /**
     * An error larger than the whole budget could only be held by evicting the history it explains,
     * so it is not held: it goes out at once, like any event of a collected session - an ANR, which
     * carries every thread's stack, is the usual one, and the app is often killed right after. The
     * history it earned still waits for its own jitter, so it may reach the intake after the error.
     */
    private fun writeOversizedError(
        writer: EventBatchWriter,
        sessionId: String,
        batchEvent: RawBatchEvent,
        eventType: EventType
    ): Boolean {
        val written = delegate.writeSerialized(writer, batchEvent, eventType)
        releaseIfEarned(writer, sessionId, isCrash = false)
        return written
    }

    @WorkerThread
    private fun holdView(viewId: String, date: Long, batchEvent: RawBatchEvent, eventType: EventType) {
        // A view event is cumulative, so the latest supersedes the ones before. Removed first so the
        // map orders views by their last update.
        views.remove(viewId)
        views[viewId] = HeldView(viewId, date, batchEvent, eventType)
        // A late update of a view that already ended must not make it current again, or `prune`
        // would drop the view the next error hangs from.
        if (date >= currentViewDate) {
            currentViewDate = date
            currentViewId = viewId
        }
        while (views.size > VIEWS_LIMIT) {
            val oldest = views.keys.firstOrNull { it != currentViewId } ?: break
            views.remove(oldest)
        }
        prune()
    }

    /**
     * An event larger than the whole budget would evict the minute before it to make room it never
     * fits into, so it is dropped - unless it is a crash, the only error that reaches here that large
     * (see [writeOversizedError]): it is held apart from the budget and released at once, after the
     * views it belongs to.
     */
    @WorkerThread
    private fun holdDetail(
        viewId: String?,
        batchEvent: RawBatchEvent,
        eventType: EventType,
        tier: EvictionTier,
        isError: Boolean
    ) {
        val size = batchEvent.data.size
        val oversized = size > BYTES_LIMIT
        if (oversized && !isError) {
            droppedCount++
            return
        }
        details.add(
            HeldEvent(
                viewId = viewId,
                event = batchEvent,
                eventType = eventType,
                heldAtNs = elapsedTimeNs(),
                tier = if (oversized) EvictionTier.KEPT else tier,
                budgeted = !oversized
            )
        )
        if (!oversized) bytes += size
        prune()
        while (details.size > EVENTS_LIMIT || bytes > BYTES_LIMIT) {
            if (!evictOne()) break
        }
    }

    /** Drops what has aged out of the window, and the views left with nothing in it. */
    private fun prune() {
        // Once a release is scheduled the window stops moving: a delayed timer must not throw away
        // the very minute before the error that it exists to deliver.
        val now = releaseScheduledAtNs ?: elapsedTimeNs()
        val oldestAllowed = now - WINDOW_NS
        val iterator = details.iterator()
        while (iterator.hasNext()) {
            val held = iterator.next()
            if (held.heldAtNs >= oldestAllowed) break
            if (held.budgeted) bytes -= held.event.data.size
            droppedCount++
            iterator.remove()
        }
        // A view is kept as the container of its detail; the one in progress always stays, it is
        // the container the error will hang from.
        val viewsWithDetail = details.mapNotNullTo(HashSet()) { it.viewId }
        views.keys.retainAll { it == currentViewId || it in viewsWithDetail }
    }

    private fun evictOne(): Boolean {
        for (tier in EVICTED_BEFORE_ERRORS) {
            val index = details.indexOfFirst { it.tier == tier }
            if (index != -1) {
                evictAt(index)
                return true
            }
        }
        val index = details.indexOfLast { it.tier == EvictionTier.LAST_RESORT }
        if (index == -1) return false
        evictAt(index)
        return true
    }

    private fun evictAt(index: Int) {
        bytes -= details.removeAt(index).event.data.size
        droppedCount++
    }

    @Suppress("ThreadSafety") // the scheduled release is handed a writer on the storage thread
    private fun scheduleReleaseOnce(sessionId: String) {
        if (releaseScheduledAtNs != null) return
        releaseScheduledAtNs = elapsedTimeNs()
        val scheduledGeneration = generation
        scheduledRelease = scheduleRelease(
            computeReleaseDelayMs(sessionId),
            { writer ->
                synchronized(this) {
                    if (generation == scheduledGeneration && withheldForSessionId == sessionId) {
                        release(writer)
                    }
                }
            },
            {
                synchronized(this) {
                    if (generation == scheduledGeneration && withheldForSessionId == sessionId) {
                        clear()
                        withheldForSessionId = null
                    }
                }
            }
        )
    }

    /**
     * Writes what is held. [replayGoesOut] is false when the process is going down: the replay
     * held in memory never reaches storage then, so the events claim only the records already
     * written.
     */
    @WorkerThread
    private fun release(writer: EventBatchWriter, replayGoesOut: Boolean = true) {
        val sessionId = withheldForSessionId
        prune()
        // Views only set the order: the session is built out of whichever of its views arrives
        // first, so that one has to be the earliest. Every detail goes, whether or not its view is
        // still held - an error raised before the first view, or whose view was evicted, is still
        // the error the session is kept for. Then the errors - a release at exit leaves in as many
        // requests as the process still gets to send, and the error is what the session is kept
        // for - then the rest.
        val (errors, others) = details.partition { it.tier >= EvictionTier.LAST_RESORT }
        // What storage refused is counted, so the release reports what actually landed.
        val writeFailures = views.values.sortedBy { it.date }
            .count { !write(writer, it.event, it.eventType, it.viewId, replayGoesOut) } +
            (errors + others).count { !write(writer, it.event, it.eventType, it.viewId, replayGoesOut) }

        // Without this the promise of a minute of history before the error could not be checked.
        internalLogger.log(
            level = InternalLogger.Level.INFO,
            target = InternalLogger.Target.TELEMETRY,
            messageBuilder = { RELEASED_MESSAGE },
            throwable = null,
            onlyOnce = false,
            additionalProperties = mapOf(
                "buffer.views_count" to views.size,
                "buffer.events_count" to details.size,
                "buffer.dropped_count" to droppedCount,
                "buffer.bytes" to bytes,
                "buffer.write_failures" to writeFailures
            )
        )

        clear()
        withheldForSessionId = null
        // After the events, so the replay is never written ahead of the session it belongs to. Not
        // on a crash: the events claimed no held replay, and one written in the crash handler's
        // last moments would be stored with nothing to open it from. Should the process survive -
        // a wrapper can report a fatal error and carry on - the session's next event lets it out,
        // and the views written from then on claim it.
        if (replayGoesOut) sessionId?.let(::notifyReplayReleased) else crashReleasedSessionId = sessionId
    }

    private fun notifyReplayReleased(sessionId: String) {
        replayReleasedSessionId = sessionId
        releaseReplay(sessionId)
    }

    private fun write(
        writer: EventBatchWriter,
        event: RawBatchEvent,
        eventType: EventType,
        viewId: String?,
        includeHeldReplay: Boolean
    ): Boolean = delegate.writeSerialized(
        writer,
        viewId?.let { claimReplay(event, it, includeHeldReplay) } ?: event,
        eventType
    )

    /**
     * An event assembled while the replay was withheld could not claim it then, since the replay
     * might have been dropped. It goes out now alongside its records, so it claims it if its view
     * has any.
     */
    private fun claimReplay(event: RawBatchEvent, viewId: String, includeHeld: Boolean): RawBatchEvent {
        if (replayRecordsCount(viewId, includeHeld) <= 0) return event
        val json = try {
            JsonParser.parseString(String(event.data, Charsets.UTF_8)).asJsonObject
        } catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
            return event
        }
        val session = json.getAsJsonObject(SESSION_KEY) ?: return event
        session.addProperty(HAS_REPLAY_KEY, true)
        return event.copy(data = json.toString().toByteArray(Charsets.UTF_8))
    }

    private fun clear() {
        generation++
        releaseScheduledAtNs = null
        scheduledRelease = null
        holdingClosed = false
        views.clear()
        details.clear()
        bytes = 0L
        droppedCount = 0
        currentViewId = null
        currentViewDate = Long.MIN_VALUE
    }

    // endregion

    internal companion object {

        /** How much history a withheld session keeps: the minute leading up to its error. */
        internal val WINDOW_NS = TimeUnit.SECONDS.toNanos(60)

        /** Memory bound on everything but views. Above it the least valuable events go first. */
        internal const val BYTES_LIMIT = 64 * 1024
        internal const val EVENTS_LIMIT = 200

        /**
         * Views are the containers their events hang from, so they are kept out of the budget above;
         * this only bounds pathological view counts.
         */
        internal const val VIEWS_LIMIT = 50

        /**
         * Correlated errors make a whole fleet release at the same instant, right when whatever
         * caused them is under strain. Releases are spread over this window instead.
         */
        internal const val RELEASE_MAX_DELAY_MS = 3_000L

        /**
         * A stopped session keeps draining its pending requests while the sessions after it come and
         * go, so more are remembered than a browser tab would need.
         */
        private const val DISCARDED_SESSIONS_REMEMBERED = 64

        private const val HTTP_ERROR_STATUS = 400L

        private val EVICTED_BEFORE_ERRORS = listOf(EvictionTier.FIRST, EvictionTier.LAST)

        private const val SESSION_KEY = "session"
        private const val HAS_REPLAY_KEY = "has_replay"
        private const val ID_KEY = "id"
        private const val TYPE_KEY = "type"
        private const val VIEW_KEY = "view"
        private const val DATE_KEY = "date"
        private const val ERROR_KEY = "error"
        private const val SOURCE_KEY = "source"
        private const val RESOURCE_KEY = "resource"
        private const val STATUS_CODE_KEY = "status_code"
        private const val WEB_VIEW_TYPE = "view"
        private const val WEB_ERROR_TYPE = "error"
        private const val WEB_RESOURCE_TYPE = "resource"
        private const val WEB_LONG_TASK_TYPE = "long_task"
        private const val WEB_AGENT_SOURCE = "agent"

        internal const val RELEASED_MESSAGE = "Error session event buffer released"

        /**
         * Deterministic per session. Multiplicative (String.hashCode is `h * 31 + c`) rather than a
         * sum of characters: session ids are same-length strings over one small alphabet, and summing
         * them lands nearly every session within the same few hundred milliseconds.
         */
        internal fun computeReleaseDelayMs(sessionId: String): Long =
            sessionId.hashCode().toLong().absoluteValue % RELEASE_MAX_DELAY_MS

        private fun sessionIdOf(element: Any): String? = when (element) {
            is ViewEvent -> element.session.id
            is ActionEvent -> element.session.id
            is ResourceEvent -> element.session.id
            is ErrorEvent -> element.session.id
            is LongTaskEvent -> element.session.id
            is VitalAppLaunchEvent -> element.session.id
            is VitalOperationStepEvent -> element.session.id
            else -> null
        }

        private fun viewIdOf(element: Any): String? = when (element) {
            is ActionEvent -> element.view.id
            is ResourceEvent -> element.view.id
            is ErrorEvent -> element.view.id
            is LongTaskEvent -> element.view.id
            is VitalAppLaunchEvent -> element.view?.id
            is VitalOperationStepEvent -> element.view.id
            else -> null
        }

        private fun evictionTierOf(element: Any): EvictionTier = when (element) {
            is ErrorEvent -> if (element.error.isCrash == true) EvictionTier.KEPT else EvictionTier.LAST_RESORT
            is LongTaskEvent -> EvictionTier.FIRST
            is ResourceEvent -> resourceTier(element.resource.statusCode)
            else -> EvictionTier.LAST
        }

        private fun webEvictionTierOf(type: String?, event: JsonObject, isError: Boolean): EvictionTier = when {
            isError -> EvictionTier.LAST_RESORT
            type == WEB_LONG_TASK_TYPE -> EvictionTier.FIRST
            type == WEB_RESOURCE_TYPE -> resourceTier(event.longAt(RESOURCE_KEY, STATUS_CODE_KEY))
            else -> EvictionTier.LAST
        }

        /**
         * A request that failed is part of how the error happened; one that succeeded rarely is. An
         * unknown status is treated like an ordinary success.
         */
        private fun resourceTier(statusCode: Long?): EvictionTier =
            if (statusCode == 0L || (statusCode != null && statusCode >= HTTP_ERROR_STATUS)) {
                EvictionTier.LAST
            } else {
                EvictionTier.FIRST
            }

        private fun JsonObject.stringAt(vararg path: String): String? =
            primitiveAt(path)?.takeIf { it.isString }?.asString

        private fun JsonObject.longAt(vararg path: String): Long? =
            primitiveAt(path)?.takeIf { it.isNumber }?.asLong

        private fun JsonObject.primitiveAt(path: Array<out String>): JsonPrimitive? {
            var node: JsonElement = this
            for (key in path) {
                node = (node as? JsonObject)?.get(key) ?: return null
            }
            return node as? JsonPrimitive
        }
    }
}
