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
import com.datadog.android.sessionreplay.internal.processor.EnrichedResource
import com.datadog.android.sessionreplay.model.MobileSegment
import java.util.concurrent.TimeUnit

/**
 * Writes the records - and, through [ResourcesWriter], the images they refer to - unless their
 * session is one whose replay is kept only in case it reports an error (FLASHCAT FORK). Those are
 * held in memory until the session is released, and thrown away if it never is: an image is part of
 * what the screen showed, and must no more reach the intake than the records do.
 */
internal class SessionReplayRecordWriter(
    private val sdkCore: FeatureSdkCore,
    private val recordCallback: RecordCallback,
    private val resourcesWriter: ResourcesWriter
) : RecordWriter, ResourcesWriter {

    private class HeldRecord(val record: EnrichedRecord, val data: ByteArray)

    private class HeldResource(val resource: EnrichedResource, val onWritten: () -> Unit)

    // Everything below is touched on the storage thread only, in submission order, which keeps the
    // control calls in step with the records they concern.

    /** The session whose records are held rather than written. */
    private var withheldSessionId: String? = null
    private val held = ArrayList<HeldRecord>()
    private var heldBytes = 0L
    private val heldResources = LinkedHashMap<String, HeldResource>()
    private var heldResourceBytes = 0L
    private var droppedCount = 0

    /**
     * The latest meta and focus records of each view held. A view's first segment needs them to
     * be playable, and cutting the buffer at a periodic full snapshot leaves them behind.
     */
    private val viewStartRecords = HashMap<String, List<MobileSegment.MobileRecord>>()

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
                writeNow(writer, record, serialize(record))
            }
        }
    }

    override fun write(enrichedResource: EnrichedResource, sessionId: String, onWritten: () -> Unit) {
        onStorageThread {
            when (sessionId) {
                in discardedSessionIds -> Unit
                withheldSessionId -> holdResource(enrichedResource, onWritten)
                else -> resourcesWriter.write(enrichedResource, sessionId, onWritten)
            }
        }
    }

    /**
     * Holds the records of [sessionId] from now on. Whatever another session still held never
     * earned its release, so it is thrown away.
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
     * Stops holding. If [releasedSessionId] is the session held, what it held is written now, in
     * order, and the records that follow are written as they come; anything held for another
     * session is thrown away.
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
        record.records.filter { it.isViewStart() }.takeIf { it.isNotEmpty() }?.let {
            viewStartRecords[record.viewId] = it
        }
        val data = serialize(record)
        held.add(HeldRecord(record, data))
        heldBytes += data.size
        recordCallback.onRecordForViewWithheld(record)
        // Only the last minute is kept, and history can only be cut where it can be replayed from:
        // at a full snapshot. Kept from the newest full snapshot that is at least a minute old, so
        // what is released always spans the whole minute and starts playable.
        val newest = record.fullSnapshotTimestamp() ?: return
        val firstInWindow = held.indexOfLast {
            (it.record.fullSnapshotTimestamp() ?: Long.MAX_VALUE) <= newest - WINDOW_MS
        }
        if (firstInWindow > 0) dropOldest(firstInWindow)
        // Memory bound: the oldest span goes first, down to the latest full snapshot at least.
        while (heldBytes > BYTES_LIMIT) {
            val nextFullSnapshot = held.indexOfFirstFrom(1) { it.record.fullSnapshotTimestamp() != null }
            if (nextFullSnapshot == -1) break
            dropOldest(nextFullSnapshot)
        }
    }

    private fun holdResource(resource: EnrichedResource, onWritten: () -> Unit) {
        if (heldResources.containsKey(resource.filename)) return
        heldResources[resource.filename] = HeldResource(resource, onWritten)
        heldResourceBytes += resource.resource.size
        // Images are bounded apart from the records; the oldest go first. A record released without
        // its image shows a placeholder, it does not break the replay.
        while (heldResourceBytes > BYTES_LIMIT && heldResources.size > 1) {
            val oldest = heldResources.keys.first()
            heldResourceBytes -= heldResources.remove(oldest)?.resource?.resource?.size ?: 0
        }
    }

    private fun dropOldest(count: Int) {
        val dropped = held.subList(0, count)
        heldBytes -= dropped.sumOf { it.data.size.toLong() }
        droppedCount += dropped.size
        recordCallback.onWithheldRecordsCleared(dropped.map { it.record })
        dropped.clear()
    }

    private fun release(writer: EventBatchWriter) {
        val first = held.firstOrNull()?.record
        val startRecords = first?.let { viewStartRecords[it.viewId] }
        held.forEachIndexed { index, heldRecord ->
            if (index == 0 && first != null && startRecords != null && first.records.none { it.isViewStart() }) {
                // Cut at a periodic full snapshot: the view's meta and focus were left behind, and a
                // segment cannot be played without them.
                val timestamp = first.fullSnapshotTimestamp() ?: startRecords.first().timestamp()
                val record = first.copy(records = startRecords.map { it.at(timestamp) } + first.records)
                writeNow(writer, record, serialize(record))
            } else {
                writeNow(writer, heldRecord.record, heldRecord.data)
            }
        }
        heldResources.values.forEach { resourcesWriter.write(it.resource, withheldSessionId.orEmpty(), it.onWritten) }
        recordCallback.onWithheldRecordsCleared(held.map { it.record })
        // Without this the replay's promise of a minute before the error could not be checked.
        sdkCore.internalLogger.log(
            level = InternalLogger.Level.INFO,
            target = InternalLogger.Target.TELEMETRY,
            messageBuilder = { RELEASED_MESSAGE },
            throwable = null,
            onlyOnce = false,
            additionalProperties = mapOf(
                "buffer.records_count" to held.sumOf { it.record.records.size },
                "buffer.duration_ms" to held.durationMs(),
                "buffer.dropped_count" to droppedCount
            )
        )
        reset()
    }

    private fun discardHeld() {
        val sessionId = withheldSessionId ?: return
        discardedSessionIds.addLast(sessionId)
        if (discardedSessionIds.size > DISCARDED_SESSIONS_REMEMBERED) {
            discardedSessionIds.removeFirstOrNull()
        }
        recordCallback.onWithheldRecordsCleared(held.map { it.record })
        reset()
    }

    private fun reset() {
        held.clear()
        heldBytes = 0L
        heldResources.clear()
        heldResourceBytes = 0L
        viewStartRecords.clear()
        droppedCount = 0
        withheldSessionId = null
    }

    private fun writeNow(writer: EventBatchWriter, record: EnrichedRecord, data: ByteArray) {
        val success = writer.write(
            event = RawBatchEvent(data = data),
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

        /** Memory bound on what a withheld session holds. */
        internal const val BYTES_LIMIT = 4L * 1024 * 1024

        private const val DISCARDED_SESSIONS_REMEMBERED = 4

        internal const val RELEASED_MESSAGE = "Error session replay buffer released"

        private fun serialize(record: EnrichedRecord) = record.toJson().toByteArray(Charsets.UTF_8)

        private fun EnrichedRecord.fullSnapshotTimestamp(): Long? =
            records.firstNotNullOfOrNull { (it as? MobileSegment.MobileRecord.MobileFullSnapshotRecord)?.timestamp }

        private fun MobileSegment.MobileRecord.isViewStart() =
            this is MobileSegment.MobileRecord.MetaRecord || this is MobileSegment.MobileRecord.FocusRecord

        private fun MobileSegment.MobileRecord.timestamp(): Long = when (this) {
            is MobileSegment.MobileRecord.MetaRecord -> timestamp
            is MobileSegment.MobileRecord.FocusRecord -> timestamp
            else -> 0L
        }

        private fun MobileSegment.MobileRecord.at(timestamp: Long): MobileSegment.MobileRecord = when (this) {
            is MobileSegment.MobileRecord.MetaRecord -> copy(timestamp = timestamp)
            is MobileSegment.MobileRecord.FocusRecord -> copy(timestamp = timestamp)
            else -> this
        }

        private inline fun <T> List<T>.indexOfFirstFrom(from: Int, predicate: (T) -> Boolean): Int {
            for (index in from until size) {
                if (predicate(this[index])) return index
            }
            return -1
        }

        private fun List<HeldRecord>.durationMs(): Long {
            val stamps = mapNotNull { it.record.fullSnapshotTimestamp() }
            return (stamps.maxOrNull() ?: 0L) - (stamps.minOrNull() ?: 0L)
        }
    }
}
