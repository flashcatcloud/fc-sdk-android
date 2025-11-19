/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sessionreplay.internal.storage

import com.flashcat.rum.api.context.FlashcatContext
import com.flashcat.rum.api.feature.EventWriteScope
import com.flashcat.rum.api.feature.Feature
import com.flashcat.rum.api.feature.FeatureScope
import com.flashcat.rum.api.feature.FeatureSdkCore
import com.flashcat.rum.api.storage.EventBatchWriter
import com.flashcat.rum.api.storage.EventType
import com.flashcat.rum.api.storage.RawBatchEvent
import com.flashcat.rum.sessionreplay.forge.ForgeConfigurator
import com.flashcat.rum.sessionreplay.internal.processor.EnrichedResource
import com.flashcat.rum.sessionreplay.internal.processor.asBinaryMetadata
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
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
    lateinit var fakeFlashcatContext: FlashcatContext

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
            val callback = it.getArgument<(FlashcatContext, EventWriteScope) -> Unit>(it.arguments.lastIndex)
            callback.invoke(fakeFlashcatContext, mockEventWriteScope)
        }
        fakeFlashcatContext = fakeFlashcatContext.copy(
            featuresContext = fakeFlashcatContext.featuresContext
                .toMutableMap().apply {
                    put(Feature.RUM_FEATURE_NAME, mapOf("application_id" to fakeRumApplicationId.toString()))
                }
        )

        // When
        testedWriter.write(fakeEnrichedResource)

        // Then
        val metadataBytearray = fakeEnrichedResource.asBinaryMetadata(fakeRumApplicationId.toString())
        verify(mockEventBatchWriter).write(
            event = RawBatchEvent(data = fakeEnrichedResource.resource, metadata = metadataBytearray),
            batchMetadata = null,
            eventType = EventType.DEFAULT
        )
    }
}
