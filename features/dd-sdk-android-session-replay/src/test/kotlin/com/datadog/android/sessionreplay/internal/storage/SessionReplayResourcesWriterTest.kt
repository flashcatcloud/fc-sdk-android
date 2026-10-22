/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.storage

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.processor.EnrichedResource
import com.datadog.android.sessionreplay.internal.processor.asBinaryMetadata
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.UUID

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class SessionReplayResourcesWriterTest {
    private lateinit var testedWriter: SessionReplayResourcesWriter

    @Mock
    lateinit var mockFeatureSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockResourcesFeature: FeatureScope

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    @Mock
    lateinit var mockEventWriteScope: EventWriteScope

    @Forgery
    lateinit var fakeEnrichedResource: EnrichedResource

    @Forgery
    lateinit var fakeRumApplicationId: UUID

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @BeforeEach
    fun setup() {
        whenever(mockEventBatchWriter.write(anyOrNull(), anyOrNull(), any()))
            .thenReturn(true)

        whenever(mockFeatureSdkCore.getFeature(Feature.SESSION_REPLAY_RESOURCES_FEATURE_NAME))
            .thenReturn(mockResourcesFeature)

        testedWriter = SessionReplayResourcesWriter(
            sdkCore = mockFeatureSdkCore
        )
    }

    @Test
    fun `M write the resource W write()`() {
        // Given
        whenever(mockEventWriteScope.invoke(any())) doAnswer {
            val callback = it.getArgument<(EventBatchWriter) -> Unit>(0)
            callback.invoke(mockEventBatchWriter)
        }
        whenever(mockResourcesFeature.withWriteContext(eq(setOf(Feature.RUM_FEATURE_NAME)), any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventWriteScope) -> Unit>(it.arguments.lastIndex)
            callback.invoke(fakeDatadogContext, mockEventWriteScope)
        }
        fakeDatadogContext = fakeDatadogContext.copy(
            featuresContext = fakeDatadogContext.featuresContext
                .toMutableMap().apply {
                    put(Feature.RUM_FEATURE_NAME, mapOf("application_id" to fakeRumApplicationId.toString()))
                }
        )

        whenever(mockEventBatchWriter.write(any(), anyOrNull(), any())) doReturn true
        var written = 0

        // When
        testedWriter.write(fakeEnrichedResource, "session") { written++ }

        // Then
        val metadataBytearray = fakeEnrichedResource.asBinaryMetadata(fakeRumApplicationId.toString())
        verify(mockEventBatchWriter).write(
            event = RawBatchEvent(data = fakeEnrichedResource.resource, metadata = metadataBytearray),
            batchMetadata = null,
            eventType = EventType.DEFAULT
        )
        assertThat(written).isEqualTo(1)
    }

    @Test
    fun `M write the resource W write() { feature unregistered by the core since }`() {
        // Given - the core takes the feature out of its registry before stopping the features
        whenever(mockEventWriteScope.invoke(any())) doAnswer {
            it.getArgument<(EventBatchWriter) -> Unit>(0).invoke(mockEventBatchWriter)
        }
        whenever(mockResourcesFeature.withWriteContext(any(), any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventWriteScope) -> Unit>(it.arguments.lastIndex)
            callback.invoke(fakeDatadogContext, mockEventWriteScope)
        }
        whenever(mockFeatureSdkCore.getFeature(Feature.SESSION_REPLAY_RESOURCES_FEATURE_NAME)).thenReturn(null)
        var written = 0

        // When
        testedWriter.write(fakeEnrichedResource, "session") { written++ }

        // Then
        assertThat(written).isEqualTo(1)
    }

    @Test
    fun `M run the block behind the queued writes W afterQueuedWrites()`() {
        // Given - the write scope queues, as the real one does
        val queued = mutableListOf<() -> Unit>()
        whenever(mockEventWriteScope.invoke(any())) doAnswer {
            val callback = it.getArgument<(EventBatchWriter) -> Unit>(0)
            queued.add { callback.invoke(mockEventBatchWriter) }
            Unit
        }
        whenever(mockResourcesFeature.withWriteContext(any(), any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventWriteScope) -> Unit>(it.arguments.lastIndex)
            callback.invoke(fakeDatadogContext, mockEventWriteScope)
        }
        val order = mutableListOf<String>()
        testedWriter.write(fakeEnrichedResource, "session") { order.add("image") }

        // When
        testedWriter.afterQueuedWrites { order.add("done") }
        queued.forEach { it() }

        // Then
        assertThat(order).containsExactly("image", "done")
    }
}
