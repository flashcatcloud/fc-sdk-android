/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.webview.internal.replay

import com.flashcat.rum.api.InternalLogger
import com.flashcat.rum.api.context.FlashcatContext
import com.flashcat.rum.api.feature.EventWriteScope
import com.flashcat.rum.api.feature.Feature
import com.flashcat.rum.api.feature.FeatureScope
import com.flashcat.rum.api.storage.DataWriter
import com.flashcat.rum.api.storage.EventBatchWriter
import com.flashcat.rum.api.storage.EventType
import com.flashcat.rum.core.InternalSdkCore
import com.flashcat.rum.utils.forge.Configurator
import com.flashcat.rum.utils.verifyLog
import com.flashcat.rum.webview.internal.rum.WebViewRumEventContextProvider
import com.flashcat.rum.webview.internal.rum.domain.RumContext
import com.google.gson.JsonObject
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class WebViewReplayEventConsumerTest {

    private lateinit var testedConsumer: WebViewReplayEventConsumer

    @Forgery
    lateinit var fakeMappedEvent: JsonObject

    @LongForgery
    var fakeServerTimeOffsetInMillis: Long = 0L

    @Mock
    lateinit var mockWebViewReplayMapper: WebViewReplayEventMapper

    @Mock
    lateinit var mockRumContextProvider: WebViewRumEventContextProvider

    @Mock
    lateinit var mockSdkCore: InternalSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockSessionReplayFeatureScope: FeatureScope

    @Forgery
    lateinit var fakeFlashcatContext: FlashcatContext

    @Forgery
    lateinit var fakeRumContext: RumContext

    lateinit var fakeSessionReplayFeatureContext: Map<String, Any?>

    lateinit var fakeValidBrowserEvent: JsonObject

    @Mock
    lateinit var mockDataWriter: DataWriter<JsonObject>

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    @Mock
    lateinit var mockEventWriteScope: EventWriteScope

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeSessionReplayFeatureContext = forge.aMap {
            WebViewReplayEventConsumer.SESSION_REPLAY_ENABLED_KEY to true
        }
        fakeValidBrowserEvent = forge.getForgery()
        fakeRumContext = fakeRumContext.copy(
            sessionState =
            WebViewReplayEventConsumer.SESSION_TRACKED_STATE
        )
        fakeFlashcatContext = fakeFlashcatContext.copy(
            time = fakeFlashcatContext.time.copy(
                serverTimeOffsetMs = fakeServerTimeOffsetInMillis
            ),
            featuresContext = forge.aMap {
                Feature.SESSION_REPLAY_FEATURE_NAME to fakeSessionReplayFeatureContext
            }
        )
        whenever(
            mockRumContextProvider.getRumContext(any())
        ) doReturn fakeRumContext

        whenever(
            mockSdkCore.getFeature(WebViewReplayFeature.WEB_REPLAY_FEATURE_NAME)
        ) doReturn mockSessionReplayFeatureScope
        whenever(mockEventWriteScope.invoke(any())) doAnswer {
            val callback = it.getArgument<(EventBatchWriter) -> Unit>(0)
            callback.invoke(mockEventBatchWriter)
        }
        whenever(
            mockSessionReplayFeatureScope.withWriteContext(
                eq(setOf(Feature.RUM_FEATURE_NAME, Feature.SESSION_REPLAY_FEATURE_NAME)), any()
            )
        ) doAnswer {
            val callback = it.getArgument<(FlashcatContext, EventWriteScope) -> Unit>(it.arguments.lastIndex)
            callback.invoke(fakeFlashcatContext, mockEventWriteScope)
        }
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger

        testedConsumer = WebViewReplayEventConsumer(
            mockSdkCore,
            mockDataWriter,
            mockRumContextProvider,
            mockWebViewReplayMapper
        )
    }

    @Test
    fun `M send the event W consume() { valid event }`() {
        // Given
        whenever(
            mockWebViewReplayMapper.mapEvent(
                fakeValidBrowserEvent,
                fakeRumContext,
                fakeFlashcatContext
            )
        ).thenReturn(fakeMappedEvent)

        // When
        testedConsumer.consume(fakeValidBrowserEvent)

        // Then
        verify(mockDataWriter).write(mockEventBatchWriter, fakeMappedEvent, EventType.DEFAULT)
    }

    @Test
    fun `M do nothing W consume() { sr feature not registered }`() {
        // Given
        whenever(
            mockSdkCore.getFeature(Feature.SESSION_REPLAY_FEATURE_NAME)
        ) doReturn null

        // When
        testedConsumer.consume(fakeValidBrowserEvent)

        // Then
        verifyNoInteractions(mockDataWriter)
    }

    @Test
    fun `M do nothing W consume() { sr feature not enabled }`(forge: Forge) {
        // Given
        fakeFlashcatContext = fakeFlashcatContext.copy(
            featuresContext = forge.aMap {
                Feature.SESSION_REPLAY_FEATURE_NAME to forge.aMap {
                    WebViewReplayEventConsumer.SESSION_REPLAY_ENABLED_KEY to false
                }
            }
        )

        // When
        testedConsumer.consume(fakeValidBrowserEvent)

        // Then
        verifyNoInteractions(mockDataWriter)
    }

    @Test
    fun `M do nothing W consume() { sr feature context does not exist }`() {
        // Given
        fakeFlashcatContext = fakeFlashcatContext.copy(featuresContext = mapOf())

        // When
        testedConsumer.consume(fakeValidBrowserEvent)

        // Then
        verifyNoInteractions(mockDataWriter)
    }

    @Test
    fun `M do nothing W consume() { sr feature enabled entry does not exist }`(forge: Forge) {
        // Given
        fakeFlashcatContext = fakeFlashcatContext.copy(
            featuresContext = forge.aMap {
                Feature.SESSION_REPLAY_FEATURE_NAME to mapOf()
            }
        )

        // When
        testedConsumer.consume(fakeValidBrowserEvent)

        // Then
        verifyNoInteractions(mockDataWriter)
    }

    @Test
    fun `M do nothing W consume() { flashcatContext not there }`() {
        // Given
        whenever(
            mockSdkCore.getFlashcatContext()
        ) doReturn null

        // When
        testedConsumer.consume(fakeValidBrowserEvent)

        // Then
        verifyNoInteractions(mockDataWriter)
    }

    @Test
    fun `M do nothing W consume() { rumContext not there }`() {
        // Given
        whenever(
            mockRumContextProvider.getRumContext(fakeFlashcatContext)
        ) doReturn null

        // When
        testedConsumer.consume(fakeValidBrowserEvent)

        // Then
        verifyNoInteractions(mockDataWriter)
    }

    @Test
    fun `M do nothing W consume() { rum session sampled out }`(forge: Forge) {
        // Given
        whenever(
            mockRumContextProvider.getRumContext(fakeFlashcatContext)
        ) doReturn fakeRumContext.copy(sessionState = forge.anAlphabeticalString())

        // When
        testedConsumer.consume(fakeValidBrowserEvent)

        // Then
        verifyNoInteractions(mockDataWriter)
    }

    @ParameterizedTest
    @MethodSource("mapperThrowsException")
    fun `M log an sdk error W consume { mapper throws }`(fakeException: Throwable) {
        // Given
        whenever(
            mockWebViewReplayMapper.mapEvent(
                fakeValidBrowserEvent,
                fakeRumContext,
                fakeFlashcatContext
            )
        ).thenThrow(fakeException)

        // When
        testedConsumer.consume(fakeValidBrowserEvent)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            WebViewReplayEventConsumer.JSON_PARSING_ERROR_MESSAGE,
            fakeException
        )
    }

    companion object {

        @JvmStatic
        fun mapperThrowsException(): List<Throwable> {
            return listOf(
                ClassCastException(),
                NumberFormatException(),
                IllegalStateException(),
                UnsupportedOperationException()
            )
        }
    }
}
