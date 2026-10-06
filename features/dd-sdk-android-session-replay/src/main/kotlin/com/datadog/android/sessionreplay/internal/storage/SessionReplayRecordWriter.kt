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
    private val resourcesWriter: ResourcesWriter,
    /**
     * Tells the recorder the images were dropped unsent, so it captures them again when shown:
     * it captures each image once, and would otherwise never send one dropped here.
     */
    private val forgetResources: (Collection<String>) -> Unit
) : RecordWriter, ResourcesWriter {

    private class HeldRecord(val record: EnrichedRecord, val data: ByteArray, val resourceIds: Set<String>)

    private class HeldResource(val resource: EnrichedResource, val onWritten: () -> Unit)

    /** What one session holds while its replay waits for an error. */
    private class Buffer(val sessionId: String) {
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
     * The sessions that ended while still held. RUM decides on the storage thread whether such a
     * session is released or thrown away, and its word reaches here one hop later than the next
     * session's announcement: until it does, what the session held is kept aside. Oldest first;
     * more than a couple only when storage lags several sessions behind, and then the oldest go.
     */
    private val parked = ArrayList<Buffer>()

    /**
     * The images captured while a replay was withheld, kept across sessions. The recorder captures
     * an image once per process, so one thrown away with a session that never errored would be
     * missing from every later replay that shows it. An image leaves only with records that show
     * it, so nothing of a session that never errored is ever sent.
     */
    private val heldResources = LinkedHashMap<String, HeldResource>()
    private var heldResourceBytes = 0L

    /**
     * The sessions whose held records were thrown away: records still queued for them when they
     * ended must not reach the intake on their own.
     */
    private val discardedSessionIds = ArrayDeque<String>()

    /**
     * RUM's word on the sessions it ended, true for released, noted the moment it is given rather
     * than when the storage thread gets to act on it: a session already released is never the one
     * to go when too many ended sessions wait, its records are about to be written.
     */
    private val fates = LinkedHashMap<String, Boolean>()

    override fun write(record: EnrichedRecord) {
        onStorageThread { writer, consent ->
            if (record.sessionId in discardedSessionIds) return@onStorageThread
            // A record of a session kept aside is held with it: it shares that session's fate.
            val buffer = bufferOf(record.sessionId)
            when {
                buffer == null -> {
                    writeNow(writer, record, serialize(record))
                    // The recorder captures an image once per process: one it captured while a
                    // replay was withheld leaves with the first sent record that shows it.
                    if (heldResources.isNotEmpty()) sendResources(record.resourceIds(), record.sessionId)
                }
                // Nothing may be held while consent is withdrawn, and what was held under the
                // consent now withdrawn goes too.
                consent == TrackingConsent.NOT_GRANTED -> dropAllForConsent()
                else -> hold(buffer, record)
            }
        }
    }

    override fun write(enrichedResource: EnrichedResource, sessionId: String, onWritten: () -> Unit) {
        onStorageThread { _, consent ->
            when {
                consent == TrackingConsent.NOT_GRANTED -> {
                    clearResources()
                    // Captured once, like any image: told to the recorder so it is captured again.
                    forgetResources(listOf(enrichedResource.filename))
                }
                bufferOf(sessionId) != null || sessionId in discardedSessionIds ->
                    holdResource(enrichedResource, onWritten)
                else -> resourcesWriter.write(enrichedResource, sessionId, onWritten)
            }
        }
    }

    override fun withhold(sessionId: String) {
        onStorageThread { _, _ ->
            // A session already thrown away is over: a word about it that arrives late changes nothing.
            if (sessionId in discardedSessionIds || current?.sessionId == sessionId) return@onStorageThread
            current?.let(::park)
            current = Buffer(sessionId)
        }
    }

    override fun stopWithholding(sessionId: String) {
        onStorageThread { writer, _ ->
            val buffer = current ?: return@onStorageThread
            if (buffer.sessionId == sessionId) release(writer, buffer) else park(buffer)
        }
    }

    override fun release(sessionId: String) {
        noteFate(sessionId, released = true)
        onStorageThread { writer, _ ->
            bufferOf(sessionId)?.let { release(writer, it) }
        }
    }

    override fun discard(sessionId: String) {
        noteFate(sessionId, released = false)
        onStorageThread { _, _ ->
            bufferOf(sessionId)?.let(::discard)
        }
    }

    private fun noteFate(sessionId: String, released: Boolean) {
        synchronized(this) {
            fates.remove(sessionId)
            fates[sessionId] = released
            while (fates.size > FATES_REMEMBERED) fates.remove(fates.keys.first())
        }
    }

    override fun dropForConsent() {
        onStorageThread { _, _ -> dropAllForConsent() }
    }

    private fun bufferOf(sessionId: String): Buffer? =
        current?.takeIf { it.sessionId == sessionId } ?: parked.firstOrNull { it.sessionId == sessionId }

    /** The session is no longer current: kept aside for RUM's word, unless that word was already given. */
    private fun park(buffer: Buffer) {
        if (current === buffer) current = null
        if (fates[buffer.sessionId] == false) {
            discard(buffer)
            return
        }
        parked.add(buffer)
        val waiting = parked.filter { fates[it.sessionId] != true }
        if (waiting.size > PARKED_LIMIT) discard(waiting.first())
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
        // Memory bound: the oldest span goes first. A single span over the whole budget goes too -
        // holding starts over at the next full snapshot - since the bound is a promise to the app.
        while (buffer.bytes > BYTES_LIMIT) {
            val nextFullSnapshot = buffer.records.indexOfFirstFrom(1) { it.record.fullSnapshotTimestamp() != null }
            dropOldest(buffer, if (nextFullSnapshot == -1) buffer.records.size else nextFullSnapshot)
        }
    }

    private fun holdResource(resource: EnrichedResource, onWritten: () -> Unit) {
        val size = resource.resource.size
        // One image the size of the whole budget would only ever evict every other.
        if (size > BYTES_LIMIT || heldResources.containsKey(resource.filename)) return
        heldResources[resource.filename] = HeldResource(resource, onWritten)
        heldResourceBytes += size
        // Images are bounded apart from the records; the oldest go first, and the recorder is told
        // so that it captures them again when they are shown again.
        val evicted = ArrayList<String>()
        while (heldResourceBytes > BYTES_LIMIT) {
            val oldest = heldResources.keys.first()
            heldResourceBytes -= heldResources.remove(oldest)?.resource?.resource?.size ?: 0
            evicted.add(oldest)
        }
        if (evicted.isNotEmpty()) forgetResources(evicted)
    }

    private fun dropOldest(buffer: Buffer, count: Int) {
        val dropped = buffer.records.subList(0, count)
        buffer.bytes -= dropped.sumOf { it.data.size.toLong() }
        buffer.droppedCount += dropped.size
        recordCallback.onWithheldRecordsCleared(dropped.map { it.record })
        val lastDroppedViewId = dropped.last().record.viewId
        dropped.clear()
        // A view's start is only ever needed by records still held of that view - or by the records
        // to come of the view in progress, whose span may just have been dropped whole.
        val viewsHeld = buffer.records.mapTo(HashSet()) { it.record.viewId }
        buffer.viewStartRecords.keys.retainAll(viewsHeld + lastDroppedViewId)
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
        sendResources(buffer.records.flatMapTo(HashSet()) { it.resourceIds }, buffer.sessionId)
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

    private fun sendResources(resourceIds: Set<String>, sessionId: String) {
        resourceIds.forEach { id ->
            heldResources.remove(id)?.let { held ->
                heldResourceBytes -= held.resource.resource.size
                resourcesWriter.write(held.resource, sessionId, held.onWritten)
            }
        }
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
        parked.remove(buffer)
    }

    /**
     * Consent was withdrawn: what was held under the consent now withdrawn is dropped, and the
     * sessions hold again from scratch should consent be granted back. The views' meta and focus
     * stay: they say how big the screen is, not what it showed, and the view in progress will not
     * record them again.
     */
    private fun dropAllForConsent() {
        (listOfNotNull(current) + parked).forEach { buffer ->
            recordCallback.onWithheldRecordsCleared(buffer.records.map { it.record })
            buffer.droppedCount += buffer.records.size
            // Only the view in progress will hold records again without recording its start anew.
            val viewInProgress = buffer.records.lastOrNull()?.record?.viewId
            buffer.records.clear()
            buffer.bytes = 0L
            buffer.viewStartRecords.keys.retainAll(listOfNotNull(viewInProgress))
        }
        clearResources()
    }

    private fun clearResources() {
        if (heldResources.isNotEmpty()) forgetResources(heldResources.keys.toList())
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

        /**
         * Memory bound on the serialized records a session holds, and the same again on the images.
         * The records are held as objects too, so a buffer weighs up to about twice this.
         */
        internal const val BYTES_LIMIT = 4L * 1024 * 1024

        /** How many ended sessions wait for a word RUM has not given yet; beyond that the oldest goes. */
        internal const val PARKED_LIMIT = 2

        /** More than a browser tab would need: a stopped session keeps draining while others come and go. */
        private const val DISCARDED_SESSIONS_REMEMBERED = 64

        private const val FATES_REMEMBERED = 16

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
