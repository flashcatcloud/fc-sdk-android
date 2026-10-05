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
import com.google.gson.JsonParser
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
 * worse than none), and the memory budget is the size actually uploaded.
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
     * Runs the release after the given delay, on the storage thread, with a writer for whatever
     * the tracking consent is by then.
     */
    private val scheduleRelease: (delayMs: Long, release: (EventBatchWriter) -> Unit) -> Unit,
    /**
     * How many replay records a view holds or has sent, so released events can claim the replay
     * that goes out with them.
     */
    private val replayRecordsCount: (viewId: String) -> Long,
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
    private val discardReplay: (sessionId: String) -> Unit
) : DataWriter<Any> {

    private class HeldView(val viewId: String, val date: Long, val event: RawBatchEvent, val eventType: EventType)

    private class HeldEvent(
        val viewId: String?,
        val event: RawBatchEvent,
        val eventType: EventType,
        val heldAtNs: Long,
        val tier: EvictionTier
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
         * A crash is never evicted: the process is going down, the history is released with it at
         * once, and it is what that release exists to deliver.
         */
        CRASH
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

    /** Latest event per view, least recently updated first. */
    private val views = LinkedHashMap<String, HeldView>()
    private val details = ArrayList<HeldEvent>()
    private var bytes = 0L
    private var droppedCount = 0
    private var currentViewId: String? = null
    private var currentViewDate = Long.MIN_VALUE

    /** When the release was scheduled, which is what freezes the window - see [prune]. */
    private var releaseScheduledAtNs: Long? = null

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
            if (sessionId in discardedSessionIds) {
                // Accepted and dropped, as the batch would have done had the session never been
                // collected.
                return true
            }
            if (sessionId == watchedSessionId && element is ErrorEvent) {
                // The error the console opens the replay from claims it, if its view has records.
                val claimed = if (replayRecordsCount(element.view.id) > 0) {
                    element.copy(session = element.session.copy(hasReplay = true))
                } else {
                    element
                }
                val written = delegate.write(writer, claimed, eventType)
                if (written && releasedSessionId != sessionId) {
                    releasedSessionId = sessionId
                    notifyReplayReleased(sessionId)
                }
                return written
            }
            if (sessionId != withheldForSessionId) {
                return delegate.write(writer, element, eventType)
            }
            val batchEvent = delegate.serialize(element) ?: return false
            if (element is ErrorEvent) {
                releasedSessionId = sessionId
                if (batchEvent.data.size > BYTES_LIMIT) {
                    // The session has earned its release. An error larger than the whole budget
                    // could only be held by evicting the history it explains, so it goes to the
                    // batch on its own; the history still leaves behind the jitter - unless this
                    // is a crash, after which no timer would ever fire.
                    delegate.writeSerialized(writer, claimReplay(batchEvent, element.view.id), eventType)
                    if (element.error.isCrash == true) release(writer) else scheduleReleaseOnce(sessionId)
                    return true
                }
            }
            hold(element, batchEvent, eventType)
            if (releasedSessionId == sessionId) {
                if (element is ErrorEvent && element.error.isCrash == true) {
                    // The process is going down: the crash handler waits for this write and no
                    // timer would ever fire. What was held goes now, with the crash.
                    release(writer)
                } else {
                    scheduleReleaseOnce(sessionId)
                }
            }
            return true
        }
    }

    // endregion

    // region Session lifecycle

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

    /** Watches a collected session whose replay only is kept on error for its first error. */
    @WorkerThread
    fun watchForError(sessionId: String) {
        synchronized(this) { watchedSessionId = sessionId }
    }

    /** Starts withholding the events of a session just drawn as one kept only on error. */
    @WorkerThread
    fun startWithholding(sessionId: String, writer: EventBatchWriter) {
        synchronized(this) {
            withheldForSessionId?.let { endSession(it, writer) }
            withheldForSessionId = sessionId
        }
    }

    /**
     * The session ended - expired, renewed or stopped. If it had reported an error, what it holds
     * is released now, whether or not the jitter has run out. If not, it never will, so what it holds
     * is thrown away, and so is anything of it that arrives later.
     */
    @WorkerThread
    fun endSession(sessionId: String, writer: EventBatchWriter) {
        synchronized(this) {
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
     * Tracking consent was withdrawn while the session was still withholding. What it holds was
     * collected under the consent now withdrawn, so it is dropped; the session itself goes on, and
     * holds again from scratch should consent be granted back.
     */
    @WorkerThread
    fun dropHeld(sessionId: String) {
        synchronized(this) {
            if (withheldForSessionId != sessionId || releasedSessionId == sessionId) return
            clear()
        }
    }

    // endregion

    // region Internal

    @WorkerThread
    private fun hold(element: Any, batchEvent: RawBatchEvent, eventType: EventType) {
        if (element is ViewEvent) {
            holdView(element, batchEvent, eventType)
            return
        }
        val size = batchEvent.data.size
        if (size > BYTES_LIMIT) {
            // Holding it would evict the whole minute before it to make room it never fits into.
            droppedCount++
            return
        }
        details.add(
            HeldEvent(
                viewId = viewIdOf(element),
                event = batchEvent,
                eventType = eventType,
                heldAtNs = elapsedTimeNs(),
                tier = evictionTierOf(element)
            )
        )
        bytes += size
        prune()
        while (details.size > EVENTS_LIMIT || bytes > BYTES_LIMIT) {
            if (!evictOne()) break
        }
    }

    @WorkerThread
    private fun holdView(element: ViewEvent, batchEvent: RawBatchEvent, eventType: EventType) {
        val viewId = element.view.id
        // A view event is cumulative, so the latest supersedes the ones before. Removed first so the
        // map orders views by their last update.
        views.remove(viewId)
        views[viewId] = HeldView(viewId, element.date, batchEvent, eventType)
        // Written locally like any view the batch takes, and only locally: a native crash is
        // reported at the next launch from this file, and is exactly the error such a session is
        // kept for.
        delegate.onDataWritten(element, batchEvent.data)
        // A late update of a view that already ended must not make it current again, or `prune`
        // would drop the view the next error hangs from.
        if (element.date >= currentViewDate) {
            currentViewDate = element.date
            currentViewId = viewId
        }
        while (views.size > VIEWS_LIMIT) {
            val oldest = views.keys.firstOrNull { it != currentViewId } ?: break
            views.remove(oldest)
        }
        prune()
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
            bytes -= held.event.data.size
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
        scheduleRelease(computeReleaseDelayMs(sessionId)) { writer ->
            synchronized(this) {
                if (generation == scheduledGeneration && withheldForSessionId == sessionId) {
                    release(writer)
                }
            }
        }
    }

    @WorkerThread
    private fun release(writer: EventBatchWriter) {
        val sessionId = withheldForSessionId
        prune()
        // Views only set the order: the session is built out of whichever of its views arrives
        // first, so that one has to be the earliest. Every detail goes, whether or not its view is
        // still held - an error raised before the first view, or whose view was evicted, is still
        // the error the session is kept for. Then the errors - a release at exit leaves in as many
        // requests as the process still gets to send, and the error is what the session is kept
        // for - then the rest.
        views.values.sortedBy { it.date }.forEach { write(writer, it.event, it.eventType, it.viewId) }
        val (errors, others) = details.partition { it.tier >= EvictionTier.LAST_RESORT }
        errors.forEach { write(writer, it.event, it.eventType, it.viewId) }
        others.forEach { write(writer, it.event, it.eventType, it.viewId) }

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
                "buffer.bytes" to bytes
            )
        )

        clear()
        withheldForSessionId = null
        // After the events, so the replay never reaches the intake ahead of the session it belongs
        // to. After a JVM crash this is as far as it gets: the replay is still held in memory and
        // goes down with the process - only the events, written in the crash's own write, survive.
        sessionId?.let(::notifyReplayReleased)
    }

    private fun notifyReplayReleased(sessionId: String) {
        replayReleasedSessionId = sessionId
        releaseReplay(sessionId)
    }

    private fun write(writer: EventBatchWriter, event: RawBatchEvent, eventType: EventType, viewId: String?) {
        delegate.writeSerialized(writer, viewId?.let { claimReplay(event, it) } ?: event, eventType)
    }

    /**
     * An event assembled while the replay was withheld could not claim it then, since the replay
     * might have been dropped. It goes out now alongside its records, so it claims it if its view
     * has any.
     */
    private fun claimReplay(event: RawBatchEvent, viewId: String): RawBatchEvent {
        if (replayRecordsCount(viewId) <= 0) return event
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

        private const val DISCARDED_SESSIONS_REMEMBERED = 4

        private const val HTTP_ERROR_STATUS = 400L

        private val EVICTED_BEFORE_ERRORS = listOf(EvictionTier.FIRST, EvictionTier.LAST)

        private const val SESSION_KEY = "session"
        private const val HAS_REPLAY_KEY = "has_replay"

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
            is ErrorEvent -> if (element.error.isCrash == true) EvictionTier.CRASH else EvictionTier.LAST_RESORT
            is LongTaskEvent -> EvictionTier.FIRST
            is ResourceEvent -> {
                // A request that failed is part of how the error happened; one that succeeded
                // rarely is. An unknown status is treated like an ordinary success.
                val statusCode = element.resource.statusCode
                if (statusCode == 0L || (statusCode != null && statusCode >= HTTP_ERROR_STATUS)) {
                    EvictionTier.LAST
                } else {
                    EvictionTier.FIRST
                }
            }
            else -> EvictionTier.LAST
        }
    }
}
