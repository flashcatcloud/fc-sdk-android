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
import com.datadog.android.privacy.TrackingConsent
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

    private class HeldRecord(val record: EnrichedRecord, val data: ByteArray, val resourceIds: Set<String>)

    private class HeldResource(val resource: EnrichedResource, val onWritten: () -> Unit)

    /** What one session holds while its replay waits for an error. */
    private class Buffer(val sessionId: String, val eventsWithheld: Boolean) {
        val records = ArrayList<HeldRecord>()
        var bytes = 0L
        var droppedCount = 0

        /**
         * The latest meta and focus records of each view held. A view's first segment needs them to
         * be playable, and cutting the buffer at a periodic full snapshot leaves them behind.
         */
        val viewStartRecords = HashMap<String, List<MobileSegment.MobileRecord>>()
    }

    // Everything below is touched on the storage thread only, in submission order, which keeps the
    // control calls in step with the records they concern.

    /** The session whose records are held rather than written. */
    private var current: Buffer? = null

    /**
     * A session that ended while its events were still withheld. RUM decides on the storage thread
     * whether such a session is released or thrown away, and its word can land after the next
     * session has announced itself: until it does, what the session held is kept aside.
     */
    private var parked: Buffer? = null

    /**
     * The images captured while a replay was withheld, kept across sessions. The recorder captures
     * an image once per process, so one thrown away with a session that never errored would be
     * missing from every later replay that shows it. An image leaves only with records that refer
     * to it, so nothing of a session that never errored is ever sent.
     */
    private val heldResources = LinkedHashMap<String, HeldResource>()
    private var heldResourceBytes = 0L

    /**
     * The sessions whose held records were thrown away: records still queued for them when they
     * ended must not reach the intake on their own.
     */
    private val discardedSessionIds = ArrayDeque<String>()

    override fun write(record: EnrichedRecord) {
        onStorageThread { writer, consent ->
            if (record.sessionId in discardedSessionIds) return@onStorageThread
            // A record of a session kept aside is held with it: it shares that session's fate.
            val buffer = bufferOf(record.sessionId)
            when {
                buffer == null -> writeNow(writer, record, serialize(record))
                // Nothing may be held while consent is withdrawn, and what was held under the
                // consent now withdrawn goes too.
                consent == TrackingConsent.NOT_GRANTED -> dropForConsent(buffer)
                else -> hold(buffer, record)
            }
        }
    }

    override fun write(enrichedResource: EnrichedResource, sessionId: String, onWritten: () -> Unit) {
        onStorageThread { _, consent ->
            when {
                consent == TrackingConsent.NOT_GRANTED -> clearResources()
                sessionId == current?.sessionId || sessionId == parked?.sessionId || sessionId in discardedSessionIds ->
                    holdResource(enrichedResource, onWritten)
                else -> resourcesWriter.write(enrichedResource, sessionId, onWritten)
            }
        }
    }

    override fun withhold(sessionId: String, eventsWithheld: Boolean) {
        onStorageThread { _, _ ->
            // A session already thrown away is over: a word about it that arrives late changes nothing.
            if (sessionId in discardedSessionIds || current?.sessionId == sessionId) return@onStorageThread
            current?.let(::retire)
            current = Buffer(sessionId, eventsWithheld)
        }
    }

    override fun stopWithholding(sessionId: String) {
        onStorageThread { writer, _ ->
            val buffer = current ?: return@onStorageThread
            if (buffer.sessionId == sessionId) release(writer, buffer) else retire(buffer)
        }
    }

    override fun release(sessionId: String) {
        onStorageThread { writer, _ ->
            bufferOf(sessionId)?.let { release(writer, it) }
        }
    }

    override fun discard(sessionId: String) {
        onStorageThread { _, _ ->
            bufferOf(sessionId)?.let(::discard)
        }
    }

    private fun bufferOf(sessionId: String): Buffer? = when (sessionId) {
        current?.sessionId -> current
        parked?.sessionId -> parked
        else -> null
    }

    /** The session is no longer current: kept aside for RUM's word if RUM has one, thrown away if not. */
    private fun retire(buffer: Buffer) {
        if (buffer.eventsWithheld) {
            parked?.let(::discard)
            parked = buffer
            if (current === buffer) current = null
        } else {
            discard(buffer)
        }
    }

    private fun hold(buffer: Buffer, record: EnrichedRecord) {
        record.records.filter { it.isViewStart() }.takeIf { it.isNotEmpty() }?.let {
            buffer.viewStartRecords[record.viewId] = it
        }
        val fullSnapshotAt = record.fullSnapshotTimestamp()
        // History can only be replayed from a full snapshot, so holding starts at one: a touch
        // before the first, or a mutation captured after consent was withdrawn and granted back,
        // would open the release with nothing to apply to.
        if (buffer.records.isEmpty() && fullSnapshotAt == null) {
            buffer.droppedCount++
            return
        }
        val data = serialize(record)
        buffer.records.add(HeldRecord(record, data, record.resourceIds()))
        buffer.bytes += data.size
        recordCallback.onRecordForViewWithheld(record)
        // Only the last minute is kept, and history can only be cut where it can be replayed from:
        // at a full snapshot. Kept from the newest full snapshot that is at least a minute old, so
        // what is released always spans the whole minute and starts playable.
        if (fullSnapshotAt != null) {
            val firstInWindow = buffer.records.indexOfLast {
                (it.record.fullSnapshotTimestamp() ?: Long.MAX_VALUE) <= fullSnapshotAt - WINDOW_MS
            }
            if (firstInWindow > 0) dropOldest(buffer, firstInWindow)
        }
        // Memory bound: the oldest span goes first, down to the latest full snapshot at least.
        while (buffer.bytes > BYTES_LIMIT) {
            val nextFullSnapshot = buffer.records.indexOfFirstFrom(1) { it.record.fullSnapshotTimestamp() != null }
            if (nextFullSnapshot == -1) break
            dropOldest(buffer, nextFullSnapshot)
        }
    }

    private fun holdResource(resource: EnrichedResource, onWritten: () -> Unit) {
        val size = resource.resource.size
        // One image the size of the whole budget would only ever evict every other.
        if (size > BYTES_LIMIT || heldResources.containsKey(resource.filename)) return
        heldResources[resource.filename] = HeldResource(resource, onWritten)
        heldResourceBytes += size
        // Images are bounded apart from the records; the oldest go first. A record released without
        // its image shows a placeholder, it does not break the replay.
        while (heldResourceBytes > BYTES_LIMIT) {
            val oldest = heldResources.keys.first()
            heldResourceBytes -= heldResources.remove(oldest)?.resource?.resource?.size ?: 0
        }
    }

    private fun dropOldest(buffer: Buffer, count: Int) {
        val dropped = buffer.records.subList(0, count)
        buffer.bytes -= dropped.sumOf { it.data.size.toLong() }
        buffer.droppedCount += dropped.size
        recordCallback.onWithheldRecordsCleared(dropped.map { it.record })
        dropped.clear()
        // A view's start is only ever needed by records still held of that view.
        val viewsHeld = buffer.records.mapTo(HashSet()) { it.record.viewId }
        buffer.viewStartRecords.keys.retainAll(viewsHeld)
    }

    private fun release(writer: EventBatchWriter, buffer: Buffer) {
        val first = buffer.records.firstOrNull()?.record
        val startRecords = first?.let { buffer.viewStartRecords[it.viewId] }
        buffer.records.forEachIndexed { index, heldRecord ->
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
        // Only the images these records show: any other held image belongs to what was never sent.
        buffer.records.flatMapTo(HashSet()) { it.resourceIds }.forEach { id ->
            heldResources.remove(id)?.let { held ->
                heldResourceBytes -= held.resource.resource.size
                resourcesWriter.write(held.resource, buffer.sessionId, held.onWritten)
            }
        }
        recordCallback.onWithheldRecordsCleared(buffer.records.map { it.record })
        // Without this the replay's promise of a minute before the error could not be checked.
        sdkCore.internalLogger.log(
            level = InternalLogger.Level.INFO,
            target = InternalLogger.Target.TELEMETRY,
            messageBuilder = { RELEASED_MESSAGE },
            throwable = null,
            onlyOnce = false,
            additionalProperties = mapOf(
                "buffer.records_count" to buffer.records.sumOf { it.record.records.size },
                "buffer.duration_ms" to buffer.records.durationMs(),
                "buffer.dropped_count" to buffer.droppedCount
            )
        )
        forget(buffer)
    }

    private fun discard(buffer: Buffer) {
        discardedSessionIds.addLast(buffer.sessionId)
        if (discardedSessionIds.size > DISCARDED_SESSIONS_REMEMBERED) {
            discardedSessionIds.removeFirstOrNull()
        }
        recordCallback.onWithheldRecordsCleared(buffer.records.map { it.record })
        forget(buffer)
    }

    private fun forget(buffer: Buffer) {
        if (current === buffer) current = null
        if (parked === buffer) parked = null
    }

    /**
     * Consent was withdrawn: what was held under the consent now withdrawn is dropped, and the
     * session holds again from scratch should consent be granted back.
     */
    private fun dropForConsent(buffer: Buffer) {
        recordCallback.onWithheldRecordsCleared(buffer.records.map { it.record })
        buffer.droppedCount += buffer.records.size
        buffer.records.clear()
        buffer.bytes = 0L
        buffer.viewStartRecords.clear()
        clearResources()
    }

    private fun clearResources() {
        heldResources.clear()
        heldResourceBytes = 0L
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

    private fun onStorageThread(block: (EventBatchWriter, TrackingConsent) -> Unit) {
        sdkCore.getFeature(Feature.SESSION_REPLAY_FEATURE_NAME)
            ?.withWriteContext { datadogContext, writeScope ->
                writeScope {
                    synchronized(this@SessionReplayRecordWriter) {
                        block(it, datadogContext.trackingConsent)
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

        /** Memory bound on what a withheld session holds, and the same again on the images. */
        internal const val BYTES_LIMIT = 4L * 1024 * 1024

        private const val DISCARDED_SESSIONS_REMEMBERED = 4

        internal const val RELEASED_MESSAGE = "Error session replay buffer released"

        private fun serialize(record: EnrichedRecord) = record.toJson().toByteArray(Charsets.UTF_8)

        private fun EnrichedRecord.fullSnapshotTimestamp(): Long? =
            records.firstNotNullOfOrNull { (it as? MobileSegment.MobileRecord.MobileFullSnapshotRecord)?.timestamp }

        /** The images these records show. */
        private fun EnrichedRecord.resourceIds(): Set<String> {
            val ids = HashSet<String>()
            records.forEach { record ->
                when (record) {
                    is MobileSegment.MobileRecord.MobileFullSnapshotRecord ->
                        record.data.wireframes.forEach { it.resourceId()?.let(ids::add) }
                    is MobileSegment.MobileRecord.MobileIncrementalSnapshotRecord ->
                        (record.data as? MobileSegment.MobileIncrementalData.MobileMutationData)?.let { mutation ->
                            mutation.adds.forEach { it.wireframe.resourceId()?.let(ids::add) }
                            mutation.updates.forEach {
                                (it as? MobileSegment.WireframeUpdateMutation.ImageWireframeUpdate)
                                    ?.resourceId?.let(ids::add)
                            }
                        }
                    else -> Unit
                }
            }
            return ids
        }

        private fun MobileSegment.Wireframe.resourceId(): String? =
            (this as? MobileSegment.Wireframe.ImageWireframe)?.resourceId

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
