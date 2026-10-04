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
    private val scheduleRelease: (delayMs: Long, release: (EventBatchWriter) -> Unit) -> Unit
) : DataWriter<Any> {

    private class HeldView(val date: Long, val event: RawBatchEvent, val eventType: EventType)

    private class HeldEvent(
        val viewId: String?,
        val event: RawBatchEvent,
        val eventType: EventType,
        val heldAtNs: Long,
        val tier: EvictionTier,
        val isError: Boolean
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
        LAST_RESORT
    }

    private var withheldForSessionId: String? = null

    /**
     * The session that earned its release, by an error or by being forced. Marked the moment the
     * error is seen, ahead of the release itself, so a session that ends while its release still
     * waits for the jitter is released rather than thrown away.
     */
    private var releasedSessionId: String? = null

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
            if (sessionId != withheldForSessionId) {
                return delegate.write(writer, element, eventType)
            }
            val batchEvent = delegate.serialize(element) ?: return false
            if (element is ErrorEvent) {
                releasedSessionId = sessionId
                if (batchEvent.data.size > BYTES_LIMIT) {
                    // The session has earned its release. An error larger than the whole budget
                    // could only be held by evicting the history it explains, so it goes to the
                    // batch on its own; the history still leaves behind the jitter.
                    delegate.writeSerialized(writer, batchEvent, eventType)
                    scheduleReleaseOnce(sessionId)
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
                tier = evictionTierOf(element),
                isError = element is ErrorEvent
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
        views[viewId] = HeldView(element.date, batchEvent, eventType)
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
        for (tier in listOf(EvictionTier.FIRST, EvictionTier.LAST)) {
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
        prune()
        // A detail whose view is gone has nothing to hang from at the other end. One that never had
        // a view (an app launch vital measured before the first one) hangs from the session alone.
        val releasable = details.filter { it.viewId == null || views.containsKey(it.viewId) }

        // The session is built out of whichever of its views arrives first, so that one has to be
        // the earliest. Then the errors - a release at exit leaves in as many requests as the
        // process still gets to send, and the error is what the session is kept for - then the rest.
        views.values.sortedBy { it.date }.forEach { delegate.writeSerialized(writer, it.event, it.eventType) }
        releasable.filter { it.isError }.forEach { delegate.writeSerialized(writer, it.event, it.eventType) }
        releasable.filterNot { it.isError }.forEach { delegate.writeSerialized(writer, it.event, it.eventType) }

        // Without this the promise of a minute of history before the error could not be checked.
        internalLogger.log(
            level = InternalLogger.Level.INFO,
            target = InternalLogger.Target.TELEMETRY,
            messageBuilder = { RELEASED_MESSAGE },
            throwable = null,
            onlyOnce = false,
            additionalProperties = mapOf(
                "buffer.views_count" to views.size,
                "buffer.events_count" to releasable.size,
                "buffer.dropped_count" to droppedCount,
                "buffer.bytes" to bytes
            )
        )

        clear()
        withheldForSessionId = null
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
            is ErrorEvent -> EvictionTier.LAST_RESORT
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
