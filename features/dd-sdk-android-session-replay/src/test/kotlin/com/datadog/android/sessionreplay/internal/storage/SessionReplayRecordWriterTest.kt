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
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.RecordCallback
import com.datadog.android.sessionreplay.internal.processor.EnrichedRecord
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
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
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

        testedWriter = SessionReplayRecordWriter(mockSdkCore, mockRecordCallback)
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

    private fun recordWrites() {
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
