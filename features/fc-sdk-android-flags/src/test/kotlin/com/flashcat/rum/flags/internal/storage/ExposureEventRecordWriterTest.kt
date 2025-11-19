/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.flags.internal.storage

import com.flashcat.rum.api.context.FlashcatContext
import com.flashcat.rum.api.feature.EventWriteScope
import com.flashcat.rum.api.feature.Feature
import com.flashcat.rum.api.feature.FeatureScope
import com.flashcat.rum.api.feature.FeatureSdkCore
import com.flashcat.rum.api.storage.EventBatchWriter
import com.flashcat.rum.api.storage.EventType
import com.flashcat.rum.api.storage.RawBatchEvent
import com.flashcat.rum.flags.model.ExposureEvent
import com.flashcat.rum.flags.utils.forge.ForgeConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class, ForgeExtension::class)
@ForgeConfiguration(ForgeConfigurator::class)
internal class ExposureEventRecordWriterTest {

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockFeature: FeatureScope

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    @StringForgery
    lateinit var fakeAllocationKey: String

    @StringForgery
    lateinit var fakeFlagKey: String

    @StringForgery
    lateinit var fakeVariantKey: String

    @StringForgery
    lateinit var fakeSubjectId: String

    @StringForgery
    lateinit var fakeAttributeKey: String

    @StringForgery
    lateinit var fakeAttributeValue: String

    @LongForgery
    var fakeTimestamp: Long = 0L

    private lateinit var testedWriter: ExposureEventRecordWriter

    @BeforeEach
    fun `set up`() {
        testedWriter = ExposureEventRecordWriter(sdkCore = mockSdkCore)
    }

    // region write

    @Test
    fun `M write exposure event W write() { feature available }`() {
        // Given
        val fakeAttributes = mapOf(fakeAttributeKey to fakeAttributeValue)
        val fakeEvent = ExposureEvent(
            timestamp = fakeTimestamp,
            allocation = ExposureEvent.Identifier(fakeAllocationKey),
            flag = ExposureEvent.Identifier(fakeFlagKey),
            variant = ExposureEvent.Identifier(fakeVariantKey),
            subject = ExposureEvent.Subject(
                fakeSubjectId,
                ExposureEvent.Attributes(fakeAttributes.toMutableMap())
            )
        )
        val expectedJson = fakeEvent.toJson().toString()
        val expectedBytes = expectedJson.toByteArray(Charsets.UTF_8)

        whenever(mockSdkCore.getFeature(Feature.FLAGS_FEATURE_NAME)).thenReturn(mockFeature)
        whenever(
            mockFeature.withWriteContext(
                any(),
                any()
            )
        )
            .thenAnswer { invocation ->
                val callback = invocation.getArgument<(FlashcatContext, EventWriteScope) -> Unit>(1)
                val mockContext = mock<FlashcatContext>()
                callback.invoke(mockContext) { writerScope ->
                    writerScope.invoke(mockEventBatchWriter)
                }
            }

        // When
        testedWriter.write(fakeEvent)

        // Then
        val eventCaptor = argumentCaptor<RawBatchEvent>()
        verify(mockEventBatchWriter).write(
            event = eventCaptor.capture(),
            batchMetadata = isNull(),
            eventType = eq(EventType.DEFAULT)
        )

        val capturedEvent = eventCaptor.firstValue
        assertThat(capturedEvent.data).isEqualTo(expectedBytes)
    }

    @Test
    fun `M not write W write() { feature not available }`() {
        // Given
        val fakeAttributes = mapOf(fakeAttributeKey to fakeAttributeValue)
        val fakeEvent = ExposureEvent(
            timestamp = fakeTimestamp,
            allocation = ExposureEvent.Identifier(fakeAllocationKey),
            flag = ExposureEvent.Identifier(fakeFlagKey),
            variant = ExposureEvent.Identifier(fakeVariantKey),
            subject = ExposureEvent.Subject(
                fakeSubjectId,
                ExposureEvent.Attributes(fakeAttributes.toMutableMap())
            )
        )

        whenever(mockSdkCore.getFeature(Feature.FLAGS_FEATURE_NAME)).thenReturn(null)

        // When
        testedWriter.write(fakeEvent)

        // Then
        verifyNoInteractions(mockEventBatchWriter)
    }

    @Test
    fun `M serialize event correctly W write() { complex attributes }`(forge: Forge) {
        // Given
        val complexAttributes = mapOf(
            "user_id" to forge.anAlphabeticalString(),
            "plan" to forge.anAlphabeticalString(),
            "age" to forge.anInt().toString(),
            "premium" to forge.aBool().toString(),
            "targeting_key" to forge.anAlphabeticalString()
        )
        val fakeEvent = ExposureEvent(
            timestamp = fakeTimestamp,
            allocation = ExposureEvent.Identifier(fakeAllocationKey),
            flag = ExposureEvent.Identifier(fakeFlagKey),
            variant = ExposureEvent.Identifier(fakeVariantKey),
            subject = ExposureEvent.Subject(
                fakeSubjectId,
                ExposureEvent.Attributes(complexAttributes.toMutableMap())
            )
        )

        whenever(mockSdkCore.getFeature(Feature.FLAGS_FEATURE_NAME)).thenReturn(mockFeature)
        whenever(
            mockFeature.withWriteContext(
                any(),
                any()
            )
        )
            .thenAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                val callback = invocation.arguments[1] as (FlashcatContext, ((EventBatchWriter) -> Unit) -> Unit) -> Unit
                val mockContext = mock<FlashcatContext>()
                callback.invoke(mockContext) { writerScope ->
                    writerScope.invoke(mockEventBatchWriter)
                }
            }

        // When
        testedWriter.write(fakeEvent)

        // Then
        val eventCaptor = argumentCaptor<RawBatchEvent>()
        verify(mockEventBatchWriter).write(
            event = eventCaptor.capture(),
            batchMetadata = isNull(),
            eventType = eq(EventType.DEFAULT)
        )

        val capturedEvent = eventCaptor.firstValue
        val serializedData = String(capturedEvent.data, Charsets.UTF_8)

        assertThat(serializedData).contains("\"timestamp\":$fakeTimestamp")
        assertThat(serializedData).contains("\"key\":\"$fakeAllocationKey\"")
        assertThat(serializedData).contains("\"key\":\"$fakeFlagKey\"")
        assertThat(serializedData).contains("\"key\":\"$fakeVariantKey\"")
        assertThat(serializedData).contains("\"id\":\"$fakeSubjectId\"")

        complexAttributes.forEach { (key, value) ->
            assertThat(serializedData).contains("\"$key\":\"$value\"")
        }
    }

    @Test
    fun `M handle empty attributes W write() { subject with no attributes }`() {
        // Given
        val fakeEvent = ExposureEvent(
            timestamp = fakeTimestamp,
            allocation = ExposureEvent.Identifier(fakeAllocationKey),
            flag = ExposureEvent.Identifier(fakeFlagKey),
            variant = ExposureEvent.Identifier(fakeVariantKey),
            subject = ExposureEvent.Subject(
                fakeSubjectId,
                ExposureEvent.Attributes(mutableMapOf())
            )
        )

        whenever(mockSdkCore.getFeature(Feature.FLAGS_FEATURE_NAME)).thenReturn(mockFeature)
        whenever(
            mockFeature.withWriteContext(
                any(),
                any()
            )
        )
            .thenAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                val callback = invocation.arguments[1] as (FlashcatContext, ((EventBatchWriter) -> Unit) -> Unit) -> Unit
                val mockContext = mock<FlashcatContext>()
                callback.invoke(mockContext) { writerScope ->
                    writerScope.invoke(mockEventBatchWriter)
                }
            }

        // When
        testedWriter.write(fakeEvent)

        // Then
        val eventCaptor = argumentCaptor<RawBatchEvent>()
        verify(mockEventBatchWriter).write(
            event = eventCaptor.capture(),
            batchMetadata = isNull(),
            eventType = eq(EventType.DEFAULT)
        )

        val capturedEvent = eventCaptor.firstValue
        val serializedData = String(capturedEvent.data, Charsets.UTF_8)

        // Verify basic structure is present even with empty attributes
        assertThat(serializedData).contains("\"timestamp\":$fakeTimestamp")
        assertThat(serializedData).contains("\"attributes\":{}")
    }

    @Test
    fun `M use correct parameters W write() { verify all parameters }`() {
        // Given
        val fakeAttributes = mapOf(fakeAttributeKey to fakeAttributeValue)
        val fakeEvent = ExposureEvent(
            timestamp = fakeTimestamp,
            allocation = ExposureEvent.Identifier(fakeAllocationKey),
            flag = ExposureEvent.Identifier(fakeFlagKey),
            variant = ExposureEvent.Identifier(fakeVariantKey),
            subject = ExposureEvent.Subject(
                fakeSubjectId,
                ExposureEvent.Attributes(fakeAttributes.toMutableMap())
            )
        )

        whenever(mockSdkCore.getFeature(Feature.FLAGS_FEATURE_NAME)).thenReturn(mockFeature)
        whenever(
            mockFeature.withWriteContext(
                any(),
                any()
            )
        )
            .thenAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                val callback = invocation.arguments[1] as (FlashcatContext, ((EventBatchWriter) -> Unit) -> Unit) -> Unit
                val mockContext = mock<FlashcatContext>()
                callback.invoke(mockContext) { writerScope ->
                    writerScope.invoke(mockEventBatchWriter)
                }
            }

        // When
        testedWriter.write(fakeEvent)

        // Then
        verify(mockSdkCore).getFeature(Feature.FLAGS_FEATURE_NAME)
        verify(mockFeature).withWriteContext(
            any(),
            any()
        )
        verify(mockEventBatchWriter).write(
            event = any<RawBatchEvent>(),
            batchMetadata = isNull(),
            eventType = eq(EventType.DEFAULT)
        )
    }

    // endregion
}
