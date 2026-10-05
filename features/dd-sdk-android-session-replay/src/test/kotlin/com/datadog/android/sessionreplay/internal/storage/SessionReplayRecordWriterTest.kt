/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.storage

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.RecordCallback
import com.datadog.android.sessionreplay.internal.processor.EnrichedRecord
import com.datadog.android.sessionreplay.internal.processor.EnrichedResource
import com.datadog.android.sessionreplay.model.MobileSegment
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.Mockito.mockingDetails
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.UUID

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class SessionReplayRecordWriterTest {
    lateinit var testedWriter: SessionReplayRecordWriter

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockRecordCallback: RecordCallback

    @Mock
    lateinit var mockSessionReplayFeature: FeatureScope

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    @Mock
    lateinit var mockEventWriteScope: EventWriteScope

    @BeforeEach
    fun `set up`() {
        whenever(mockEventBatchWriter.write(anyOrNull(), anyOrNull(), any()))
            .thenReturn(true)

        whenever(mockSdkCore.getFeature(Feature.SESSION_REPLAY_FEATURE_NAME))
            .thenReturn(mockSessionReplayFeature)

        testedWriter = SessionReplayRecordWriter(mockSdkCore, mockRecordCallback, mockResourcesWriter)
    }

    @Test
    fun `M write the record in a batch W write`(forge: Forge) {
        // Given
        val fakeRecord = forge.forgeEnrichedRecord()
        whenever(mockEventWriteScope.invoke(any())) doAnswer {
            val callback = it.getArgument<(EventBatchWriter) -> Unit>(0)
            callback.invoke(mockEventBatchWriter)
        }
        whenever(mockSessionReplayFeature.withWriteContext(eq(emptySet()), any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventWriteScope) -> Unit>(it.arguments.lastIndex)
            callback.invoke(fakeDatadogContext, mockEventWriteScope)
        }

        // When
        testedWriter.write(fakeRecord)

        // Then
        verify(mockEventBatchWriter).write(
            event = RawBatchEvent(data = fakeRecord.toJson().toByteArray()),
            batchMetadata = null,
            eventType = EventType.DEFAULT
        )
        verifyNoMoreInteractions(mockEventBatchWriter)

        verify(mockRecordCallback).onRecordForViewSent(fakeRecord)
        verifyNoMoreInteractions(mockRecordCallback)
    }

    @Test
    fun `M do nothing W write { feature not properly initialized }`(forge: Forge) {
        // Given
        val fakeRecord = forge.forgeEnrichedRecord()
        whenever(mockSdkCore.getFeature(Feature.SESSION_REPLAY_FEATURE_NAME))
            .thenReturn(null)

        // When
        testedWriter.write(fakeRecord)

        // Then
        verifyNoMoreInteractions(mockSessionReplayFeature)
        verifyNoMoreInteractions(mockRecordCallback)
    }

    @Test
    fun `M not call record callback W write { eventBatchWriter write failed }`(forge: Forge) {
        // Given
        whenever(mockEventBatchWriter.write(anyOrNull(), anyOrNull(), any()))
            .thenReturn(false)

        val fakeRecord = forge.forgeEnrichedRecord()
        whenever(mockEventWriteScope.invoke(any())) doAnswer {
            val callback = it.getArgument<(EventBatchWriter) -> Unit>(0)
            callback.invoke(mockEventBatchWriter)
        }
        whenever(mockSessionReplayFeature.withWriteContext(any(), any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventWriteScope) -> Unit>(it.arguments.lastIndex)
            callback.invoke(fakeDatadogContext, mockEventWriteScope)
        }

        // When
        testedWriter.write(fakeRecord)

        // Then
        verify(mockEventBatchWriter).write(
            event = RawBatchEvent(data = fakeRecord.toJson().toByteArray()),
            batchMetadata = null,
            eventType = EventType.DEFAULT
        )
        verifyNoMoreInteractions(mockEventBatchWriter)

        verifyNoMoreInteractions(mockRecordCallback)
    }

    // region Withholding

    private val written = mutableListOf<String>()

    private fun recordWrites(consent: TrackingConsent = TrackingConsent.GRANTED) {
        fakeDatadogContext = fakeDatadogContext.copy(trackingConsent = consent)
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockEventBatchWriter.write(anyOrNull(), anyOrNull(), any())) doAnswer {
            written.add(EnrichedRecordTag.of(it.getArgument<RawBatchEvent>(0)))
            true
        }
        whenever(mockEventWriteScope.invoke(any())) doAnswer {
            it.getArgument<(EventBatchWriter) -> Unit>(0).invoke(mockEventBatchWriter)
        }
        whenever(mockSessionReplayFeature.withWriteContext(any(), any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventWriteScope) -> Unit>(it.arguments.lastIndex)
            callback.invoke(fakeDatadogContext, mockEventWriteScope)
        }
    }

    /** A record tagged through its view id, with a full snapshot at [fullSnapshotAt] if given. */
    private fun record(tag: String, sessionId: String, fullSnapshotAt: Long? = null): EnrichedRecord {
        val records = listOfNotNull(
            fullSnapshotAt?.let {
                MobileSegment.MobileRecord.MobileFullSnapshotRecord(
                    it,
                    MobileSegment.Data(emptyList())
                )
            }
        )
        return EnrichedRecord("app", sessionId, tag, records)
    }

    private object EnrichedRecordTag {
        fun of(event: RawBatchEvent): String =
            com.google.gson.JsonParser.parseString(String(event.data)).asJsonObject.get("view_id").asString
    }

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockResourcesWriter: ResourcesWriter

    private fun resource(hash: String, size: Int = 10) = EnrichedResource(ByteArray(size), hash)

    @Test
    fun `M write a resource through W write(resource) { session not withheld }`() {
        // Given
        recordWrites()
        val onWritten = {}

        // When
        testedWriter.write(resource("img"), "s1", onWritten)

        // Then
        verify(mockResourcesWriter).write(any(), eq("s1"), eq(onWritten))
    }

    @Test
    fun `M hold resources and write them on release W write(resource) { session withheld }`() {
        // Given
        recordWrites()
        testedWriter.withhold("s1")
        testedWriter.write(imageRecord("r1", "s1", 1_000, "img"))
        testedWriter.write(resource("img"), "s1") {}
        testedWriter.write(resource("img"), "s1") {}

        // When
        val beforeRelease = mockingDetails(mockResourcesWriter).invocations.size
        testedWriter.stopWithholding("s1")

        // Then
        assertThat(beforeRelease).isZero()
        verify(mockResourcesWriter, times(1)).write(any(), eq("s1"), any())
    }

    @Test
    fun `M drop held resources W stopWithholding { another session }`() {
        // Given
        recordWrites()
        testedWriter.withhold("s1")
        testedWriter.write(resource("img"), "s1") {}

        // When
        testedWriter.stopWithholding("s2")
        testedWriter.write(resource("late"), "s1") {}

        // Then
        verify(mockResourcesWriter, never()).write(any(), any(), any())
    }

    @Test
    fun `M count held records apart and clear them W hold then release`() {
        // Given
        recordWrites()
        testedWriter.withhold("s1")
        val held = record("r1", "s1", fullSnapshotAt = 1_000)

        // When
        testedWriter.write(held)
        testedWriter.stopWithholding("s1")

        // Then
        inOrder(mockRecordCallback) {
            verify(mockRecordCallback).onRecordForViewWithheld(held)
            verify(mockRecordCallback).onRecordForViewSent(held)
            verify(mockRecordCallback).onWithheldRecordsCleared(listOf(held))
        }
    }

    @Test
    fun `M start the release with the view's meta and focus W release { cut at a periodic full snapshot }`() {
        // Given
        recordWrites()
        testedWriter.withhold("s1")
        val meta = MobileSegment.MobileRecord.MetaRecord(0, data = MobileSegment.Data1(100, 200))
        val focus = MobileSegment.MobileRecord.FocusRecord(0, data = MobileSegment.Data2(true))
        val full0 = MobileSegment.MobileRecord.MobileFullSnapshotRecord(0, MobileSegment.Data(emptyList()))
        testedWriter.write(EnrichedRecord("app", "s1", "view", listOf(meta, focus, full0)))
        testedWriter.write(record("view", "s1", fullSnapshotAt = 30_000))
        testedWriter.write(record("view", "s1", fullSnapshotAt = 95_000))
        val released = mutableListOf<String>()
        whenever(mockEventBatchWriter.write(anyOrNull(), anyOrNull(), any())) doAnswer {
            released.add(String(it.getArgument<RawBatchEvent>(0).data))
            true
        }

        // When
        testedWriter.stopWithholding("s1")

        // Then - cut at the full snapshot of 30s, which now carries the meta and focus first
        assertThat(released).hasSize(2)
        val first = com.google.gson.JsonParser.parseString(released[0]).asJsonObject.getAsJsonArray("records")
        assertThat(first.map { it.asJsonObject.get("type").asInt }).containsExactly(4, 6, 10)
        assertThat(first.map { it.asJsonObject.get("timestamp").asLong }).containsOnly(30_000L)
    }

    @Test
    fun `M drop the oldest span but keep a full snapshot W hold { over the byte limit }`() {
        // Given
        recordWrites()
        testedWriter.withhold("s1")
        val big = SessionReplayRecordWriter.BYTES_LIMIT.toInt() / 3
        testedWriter.write(bigRecord("old", "s1", 1_000, big))
        testedWriter.write(bigRecord("mid", "s1", 2_000, big))
        testedWriter.write(bigRecord("new", "s1", 3_000, big))

        // When
        testedWriter.stopWithholding("s1")

        // Then
        assertThat(written).containsExactly("mid", "new")
    }

    @Test
    fun `M drop a single span over the byte limit and start over W hold`() {
        // Given - the bound is a promise to the app, so even a span that cannot be cut goes
        recordWrites()
        testedWriter.withhold("s1")
        testedWriter.write(bigRecord("huge", "s1", 1_000, SessionReplayRecordWriter.BYTES_LIMIT.toInt() + 1))
        testedWriter.write(record("inc", "s1"))
        testedWriter.write(record("next", "s1", fullSnapshotAt = 4_000))

        // When
        testedWriter.stopWithholding("s1")

        // Then - holding started over at the next full snapshot
        assertThat(written).containsExactly("next")
    }

    private fun bigRecord(tag: String, sessionId: String, timestamp: Long, size: Int): EnrichedRecord {
        val text = MobileSegment.Wireframe.TextWireframe(
            id = 1,
            x = 0,
            y = 0,
            width = 1,
            height = 1,
            text = "x".repeat(size),
            textStyle = MobileSegment.TextStyle("f", 1, "#000000")
        )
        val full = MobileSegment.MobileRecord.MobileFullSnapshotRecord(timestamp, MobileSegment.Data(listOf(text)))
        return EnrichedRecord("app", sessionId, tag, listOf(full))
    }

    @Test
    fun `M hold the records W write { session withheld }`() {
        // Given
        recordWrites()
        testedWriter.withhold("s1")

        // When
        testedWriter.write(record("r1", "s1", fullSnapshotAt = 1_000))
        testedWriter.write(record("other", "s2"))

        // Then
        assertThat(written).containsExactly("other")
        // records not written are not counted for their view
        verify(mockRecordCallback).onRecordForViewSent(any())
    }

    @Test
    fun `M write what was held in order W stopWithholding { session released }`() {
        // Given
        recordWrites()
        testedWriter.withhold("s1")
        testedWriter.write(record("r1", "s1", fullSnapshotAt = 1_000))
        testedWriter.write(record("r2", "s1"))

        // When
        testedWriter.stopWithholding("s1")
        testedWriter.write(record("r3", "s1"))

        // Then
        assertThat(written).containsExactly("r1", "r2", "r3")
        verify(mockRecordCallback, times(3)).onRecordForViewSent(any())
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.INFO),
            eq(InternalLogger.Target.TELEMETRY),
            any(),
            anyOrNull(),
            eq(false),
            any()
        )
    }

    @Test
    fun `M throw away what was held and its stragglers W stopWithholding { another session }`() {
        // Given
        recordWrites()
        testedWriter.withhold("s1")
        testedWriter.write(record("r1", "s1", fullSnapshotAt = 1_000))

        // When
        testedWriter.stopWithholding("s2")
        testedWriter.write(record("late", "s1"))
        testedWriter.write(record("next", "s2"))

        // Then
        assertThat(written).containsExactly("next")
        verify(mockRecordCallback).onRecordForViewSent(any())
    }

    @Test
    fun `M throw away the previous session W withhold { new session }`() {
        // Given
        recordWrites()
        testedWriter.withhold("s1")
        testedWriter.write(record("r1", "s1", fullSnapshotAt = 1_000))

        // When
        testedWriter.withhold("s2")
        testedWriter.write(record("r2", "s2", fullSnapshotAt = 2_000))
        testedWriter.stopWithholding("s2")

        // Then
        assertThat(written).containsExactly("r2")
    }

    @Test
    fun `M keep a playable minute W write { more than a minute held }`() {
        // Given
        recordWrites()
        testedWriter.withhold("s1")
        testedWriter.write(record("full0", "s1", fullSnapshotAt = 0))
        testedWriter.write(record("inc0", "s1"))
        testedWriter.write(record("full30", "s1", fullSnapshotAt = 30_000))
        testedWriter.write(record("inc30", "s1"))
        testedWriter.write(record("full61", "s1", fullSnapshotAt = 61_000))
        testedWriter.write(record("full95", "s1", fullSnapshotAt = 95_000))

        // When
        testedWriter.stopWithholding("s1")

        // Then - cut at the newest full snapshot at least a minute older than the latest one
        assertThat(written).containsExactly("full30", "inc30", "full61", "full95")
    }

    @Test
    fun `M keep a session aside W withhold { new session }`() {
        // Given - RUM has yet to say whether the previous session is released or thrown away
        recordWrites()
        testedWriter.withhold("s1")
        testedWriter.write(record("r1", "s1", fullSnapshotAt = 1_000))

        // When
        testedWriter.withhold("s2")
        testedWriter.write(record("r2", "s2", fullSnapshotAt = 2_000))
        testedWriter.write(record("r1b", "s1"))

        // Then - a late record of the session kept aside is kept with it
        assertThat(written).isEmpty()

        // When - the previous session's word arrives
        testedWriter.release("s1")

        // Then - what it held goes, what the current one holds stays
        assertThat(written).containsExactly("r1", "r1b")

        // When
        testedWriter.release("s2")

        // Then
        assertThat(written).containsExactly("r1", "r1b", "r2")
    }

    @Test
    fun `M keep every session aside until its word W withhold { storage lags two sessions behind }`() {
        // Given - RUM's word for s1 arrives only after s2 and s3 announced themselves
        recordWrites()
        testedWriter.withhold("s1")
        testedWriter.write(record("r1", "s1", fullSnapshotAt = 1_000))
        testedWriter.withhold("s2")
        testedWriter.write(record("r2", "s2", fullSnapshotAt = 2_000))
        testedWriter.withhold("s3")
        testedWriter.write(record("r3", "s3", fullSnapshotAt = 3_000))

        // When
        testedWriter.release("s1")
        testedWriter.discard("s2")
        testedWriter.release("s3")

        // Then
        assertThat(written).containsExactly("r1", "r3")
    }

    @Test
    fun `M throw away the oldest session kept aside W withhold { too many wait for their word }`() {
        // Given
        recordWrites()
        val sessions = List(SessionReplayRecordWriter.PARKED_LIMIT + 2) { "s$it" }
        sessions.forEach {
            testedWriter.withhold(it)
            testedWriter.write(record("r-$it", it, fullSnapshotAt = 1_000))
        }

        // When - every session is released, oldest first
        sessions.forEach { testedWriter.release(it) }

        // Then - only the last PARKED_LIMIT ended sessions and the current one were still held
        assertThat(written).containsExactlyElementsOf(sessions.drop(1).map { "r-$it" })
    }

    @Test
    fun `M drop what every session holds W dropForConsent`() {
        // Given
        recordWrites()
        testedWriter.withhold("s1")
        testedWriter.write(imageRecord("r1", "s1", 1_000, "img"))
        testedWriter.write(resource("img"), "s1") {}
        testedWriter.withhold("s2")
        testedWriter.write(record("r2", "s2", fullSnapshotAt = 2_000))

        // When
        testedWriter.dropForConsent()
        testedWriter.write(record("r2b", "s2", fullSnapshotAt = 5_000))
        testedWriter.release("s1")
        testedWriter.release("s2")

        // Then - only what was held again afterwards goes
        assertThat(written).containsExactly("r2b")
        verify(mockResourcesWriter, never()).write(any(), any(), any())
    }

    @Test
    fun `M throw away a session kept aside W discard`() {
        // Given
        recordWrites()
        testedWriter.withhold("s1")
        testedWriter.write(record("r1", "s1", fullSnapshotAt = 1_000))
        testedWriter.stopWithholding("s2")

        // When
        testedWriter.discard("s1")
        testedWriter.write(record("late", "s1"))
        testedWriter.write(record("next", "s2"))

        // Then
        assertThat(written).containsExactly("next")
        verify(mockRecordCallback).onWithheldRecordsCleared(any())
    }

    @Test
    fun `M ignore a word about a session already thrown away W withhold`() {
        // Given - a stopped session may announce itself once more after the next one has
        recordWrites()
        testedWriter.withhold("s1")
        testedWriter.discard("s1")
        testedWriter.withhold("s2")
        testedWriter.write(record("r2", "s2", fullSnapshotAt = 2_000))

        // When
        testedWriter.withhold("s1")
        testedWriter.write(record("r2b", "s2"))
        testedWriter.release("s2")

        // Then
        assertThat(written).containsExactly("r2", "r2b")
    }

    @Test
    fun `M hold nothing and drop what was held W write { consent not granted }`() {
        // Given
        recordWrites()
        testedWriter.withhold("s1")
        testedWriter.write(record("r1", "s1", fullSnapshotAt = 1_000))
        testedWriter.write(resource("img"), "s1") {}

        // When
        recordWrites(consent = TrackingConsent.NOT_GRANTED)
        testedWriter.write(record("r2", "s1", fullSnapshotAt = 2_000))
        testedWriter.write(resource("img2"), "s1") {}
        recordWrites(consent = TrackingConsent.GRANTED)
        testedWriter.write(record("inc", "s1"))
        testedWriter.write(record("r3", "s1", fullSnapshotAt = 3_000))
        testedWriter.release("s1")

        // Then - holding starts over at the first full snapshot under consent
        assertThat(written).containsExactly("r3")
        verify(mockResourcesWriter, never()).write(any(), any(), any())
    }

    @Test
    fun `M drop the oldest span W hold { an incremental record takes the buffer over the byte limit }`() {
        // Given
        recordWrites()
        testedWriter.withhold("s1")
        val half = SessionReplayRecordWriter.BYTES_LIMIT.toInt() / 2
        testedWriter.write(bigRecord("old", "s1", 1_000, half))
        testedWriter.write(bigRecord("new", "s1", 2_000, half / 2))

        // When
        val inc = bigRecord("inc", "s1", 2_500, half)
        testedWriter.write(inc.copy(records = inc.records.map { incrementalOf(it) }))
        testedWriter.release("s1")

        // Then
        assertThat(written).containsExactly("new", "inc")
    }

    @Test
    fun `M send only the images the released records show W release`() {
        // Given - an image of a session that never errored, and one of the session released
        recordWrites()
        testedWriter.withhold("s1")
        testedWriter.write(resource("unseen"), "s1") {}
        testedWriter.write(imageRecord("r1", "s1", 1_000, "shared"))
        testedWriter.write(resource("shared"), "s1") {}
        testedWriter.withhold("s2")
        testedWriter.write(imageRecord("r2", "s2", 2_000, "shared"))
        testedWriter.write(resource("own"), "s2") {}
        testedWriter.write(imageRecord("r2b", "s2", 3_000, "own"))

        // When
        testedWriter.release("s2")

        // Then - the image captured under the discarded session goes with the records that show it
        val sent = argumentCaptor<EnrichedResource>()
        verify(mockResourcesWriter, times(2)).write(sent.capture(), eq("s2"), any())
        assertThat(sent.allValues.map { it.filename }).containsExactlyInAnyOrder("shared", "own")
    }

    @Test
    fun `M send a held image with a collected session's record that shows it W write`() {
        // Given - the recorder captured the image once, under a session that never errored
        recordWrites()
        testedWriter.withhold("s1")
        testedWriter.write(resource("icon"), "s1") {}
        testedWriter.stopWithholding("s2")

        // When
        testedWriter.write(imageRecord("r2", "s2", 2_000, "icon"))

        // Then
        assertThat(written).containsExactly("r2")
        val sent = argumentCaptor<EnrichedResource>()
        verify(mockResourcesWriter).write(sent.capture(), eq("s2"), any())
        assertThat(sent.firstValue.filename).isEqualTo("icon")
    }

    @Test
    fun `M start the release with the view's meta and focus W release { held again after consent }`() {
        // Given
        recordWrites()
        testedWriter.withhold("s1")
        val meta = MobileSegment.MobileRecord.MetaRecord(0, data = MobileSegment.Data1(100, 200))
        val focus = MobileSegment.MobileRecord.FocusRecord(0, data = MobileSegment.Data2(true))
        val full0 = MobileSegment.MobileRecord.MobileFullSnapshotRecord(0, MobileSegment.Data(emptyList()))
        testedWriter.write(EnrichedRecord("app", "s1", "view", listOf(meta, focus, full0)))
        recordWrites(consent = TrackingConsent.NOT_GRANTED)
        testedWriter.write(record("view", "s1", fullSnapshotAt = 3_000))
        recordWrites(consent = TrackingConsent.GRANTED)
        testedWriter.write(record("view", "s1", fullSnapshotAt = 6_000))
        val released = mutableListOf<String>()
        whenever(mockEventBatchWriter.write(anyOrNull(), anyOrNull(), any())) doAnswer {
            released.add(String(it.getArgument<RawBatchEvent>(0).data))
            true
        }

        // When
        testedWriter.release("s1")

        // Then
        assertThat(released).hasSize(1)
        val first = com.google.gson.JsonParser.parseString(released[0]).asJsonObject.getAsJsonArray("records")
        assertThat(first.map { it.asJsonObject.get("type").asInt }).containsExactly(4, 6, 10)
        assertThat(first.map { it.asJsonObject.get("timestamp").asLong }).containsOnly(6_000L)
    }

    @Test
    fun `M not hold an image over the budget W write(resource)`() {
        // Given
        recordWrites()
        testedWriter.withhold("s1")
        testedWriter.write(imageRecord("r1", "s1", 1_000, "huge"))

        // When
        testedWriter.write(resource("huge", size = SessionReplayRecordWriter.BYTES_LIMIT.toInt() + 1), "s1") {}
        testedWriter.release("s1")

        // Then
        verify(mockResourcesWriter, never()).write(any(), any(), any())
    }

    private fun imageRecord(tag: String, sessionId: String, fullSnapshotAt: Long, resourceId: String): EnrichedRecord {
        val image = MobileSegment.Wireframe.ImageWireframe(
            id = 1,
            x = 0,
            y = 0,
            width = 1,
            height = 1,
            resourceId = resourceId
        )
        val full = MobileSegment.MobileRecord.MobileFullSnapshotRecord(
            fullSnapshotAt,
            MobileSegment.Data(listOf(image))
        )
        return EnrichedRecord("app", sessionId, tag, listOf(full))
    }

    /** The same wireframes as an incremental record: an addition of each. */
    private fun incrementalOf(record: MobileSegment.MobileRecord): MobileSegment.MobileRecord {
        val full = record as MobileSegment.MobileRecord.MobileFullSnapshotRecord
        return MobileSegment.MobileRecord.MobileIncrementalSnapshotRecord(
            full.timestamp,
            MobileSegment.MobileIncrementalData.MobileMutationData(
                adds = full.data.wireframes.map { MobileSegment.Add(wireframe = it) },
                removes = emptyList(),
                updates = emptyList()
            )
        )
    }

    // endregion

    private fun Forge.forgeEnrichedRecord(): EnrichedRecord {
        // We don't want to create a forgery for this as this lives in the session-replay module
        // and we will need to copy all the records forgeries. Instead we just forge this record
        // here with empty records as it will not matter in the tests. Later if we need it we might
        // end up doing that.
        val applicationId = getForgery<UUID>().toString()
        val sessionId = getForgery<UUID>().toString()
        val viewId = getForgery<UUID>().toString()
        return EnrichedRecord(applicationId, sessionId, viewId, emptyList())
    }
}
