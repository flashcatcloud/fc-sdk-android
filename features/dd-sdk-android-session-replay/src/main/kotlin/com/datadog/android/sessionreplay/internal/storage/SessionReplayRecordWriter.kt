/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.storage

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.sessionreplay.internal.RecordCallback
import com.datadog.android.sessionreplay.internal.processor.EnrichedRecord
import com.datadog.android.sessionreplay.model.MobileSegment
import java.util.concurrent.TimeUnit

internal class SessionReplayRecordWriter(
    private val sdkCore: FeatureSdkCore,
    private val recordCallback: RecordCallback
) : RecordWriter {

    // FLASHCAT FORK - the replay of a session kept only in case it reports an error is held here
    // until it does. Everything below is touched on the storage thread only, in submission order,
    // which keeps the control calls in step with the records they concern.

    /** The session whose records are held rather than written. */
    private var withheldSessionId: String? = null
    private val held = ArrayList<EnrichedRecord>()
    private var droppedCount = 0

    /**
     * The sessions whose held records were thrown away: records still queued for them when they
     * ended must not reach the intake on their own.
     */
    private val discardedSessionIds = ArrayDeque<String>()

    override fun write(record: EnrichedRecord) {
        onStorageThread { writer ->
            if (record.sessionId in discardedSessionIds) return@onStorageThread
            if (record.sessionId == withheldSessionId) {
                hold(record)
            } else {
                writeNow(writer, record)
            }
        }
    }

    /**
     * FLASHCAT FORK - holds the records of [sessionId] from now on. Whatever another session still
     * held never earned its release, so it is thrown away.
     */
    override fun withhold(sessionId: String) {
        onStorageThread {
            if (withheldSessionId != sessionId) {
                discardHeld()
                withheldSessionId = sessionId
            }
        }
    }

    /**
     * FLASHCAT FORK - stops holding. If [releasedSessionId] is the session held, what it held is
     * written now, in order, and the records that follow are written as they come; anything held
     * for another session is thrown away.
     */
    override fun stopWithholding(releasedSessionId: String?) {
        onStorageThread { writer ->
            if (withheldSessionId != null && withheldSessionId == releasedSessionId) {
                release(writer)
            } else {
                discardHeld()
            }
        }
    }

    private fun hold(record: EnrichedRecord) {
        held.add(record)
        // Only the last minute is kept, and history can only be cut where it can be replayed from:
        // at a full snapshot. Kept from the newest full snapshot that is at least a minute old, so
        // what is released always spans the whole minute and starts playable.
        val newest = record.fullSnapshotTimestamp() ?: return
        val cutoff = newest - WINDOW_MS
        val firstKept = held.indexOfLast { (it.fullSnapshotTimestamp() ?: Long.MAX_VALUE) <= cutoff }
        if (firstKept > 0) {
            droppedCount += firstKept
            held.subList(0, firstKept).clear()
        }
    }

    private fun release(writer: EventBatchWriter) {
        held.forEach { writeNow(writer, it) }
        // Without this the replay's promise of a minute before the error could not be checked.
        sdkCore.internalLogger.log(
            level = InternalLogger.Level.INFO,
            target = InternalLogger.Target.TELEMETRY,
            messageBuilder = { RELEASED_MESSAGE },
            throwable = null,
            onlyOnce = false,
            additionalProperties = mapOf(
                "buffer.records_count" to held.sumOf { it.records.size },
                "buffer.duration_ms" to held.durationMs(),
                "buffer.dropped_count" to droppedCount
            )
        )
        held.clear()
        droppedCount = 0
        withheldSessionId = null
    }

    private fun discardHeld() {
        val sessionId = withheldSessionId ?: return
        discardedSessionIds.addLast(sessionId)
        if (discardedSessionIds.size > DISCARDED_SESSIONS_REMEMBERED) {
            discardedSessionIds.removeFirstOrNull()
        }
        held.clear()
        droppedCount = 0
        withheldSessionId = null
    }

    private fun writeNow(writer: EventBatchWriter, record: EnrichedRecord) {
        val serializedRecord = record.toJson().toByteArray(Charsets.UTF_8)
        val rawBatchEvent = RawBatchEvent(data = serializedRecord)
        val success = writer.write(
            event = rawBatchEvent,
            batchMetadata = null,
            eventType = EventType.DEFAULT
        )
        if (success) {
            // Counted only once written: a view claims a replay for records actually sent, so
            // held records that are thrown away never count.
            updateViewSent(record)
        }
    }

    private fun onStorageThread(block: (EventBatchWriter) -> Unit) {
        sdkCore.getFeature(Feature.SESSION_REPLAY_FEATURE_NAME)
            ?.withWriteContext { _, writeScope ->
                writeScope {
                    synchronized(this@SessionReplayRecordWriter) {
                        block(it)
                    }
                }
            }
    }

    private fun updateViewSent(record: EnrichedRecord) {
        /**
         * We have to see whether it's ok that this method is being called from the background.
         * However this gives us the most certainty that the records were actually queued for
         * sending, and not optimized away in the processor. Depending upon the amount of time
         * that it takes to process the nodes, the view may not be relevant anymore.
         */
        recordCallback.onRecordForViewSent(record)
    }

    internal companion object {
        /** How much replay a withheld session keeps: the minute leading up to its error. */
        internal val WINDOW_MS = TimeUnit.SECONDS.toMillis(60)

        private const val DISCARDED_SESSIONS_REMEMBERED = 4

        internal const val RELEASED_MESSAGE = "Error session replay buffer released"

        private fun EnrichedRecord.fullSnapshotTimestamp(): Long? =
            records.firstNotNullOfOrNull { (it as? MobileSegment.MobileRecord.MobileFullSnapshotRecord)?.timestamp }

        private fun List<EnrichedRecord>.durationMs(): Long {
            val stamps = mapNotNull { it.fullSnapshotTimestamp() }
            return (stamps.maxOrNull() ?: 0L) - (stamps.minOrNull() ?: 0L)
        }
    }
}
