/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.flashcat.rum.trace.internal

import com.flashcat.rum.api.InternalLogger
import com.flashcat.rum.api.context.AccountInfo
import com.flashcat.rum.api.context.FlashcatContext
import com.flashcat.rum.api.context.UserInfo
import com.flashcat.rum.api.feature.Feature
import com.flashcat.rum.api.feature.FeatureScope
import com.flashcat.rum.api.feature.SdkFeatureMock
import com.flashcat.rum.core.InternalSdkCore
import com.flashcat.rum.log.LogAttributes
import com.flashcat.rum.trace.api.span.DatadogSpan
import com.flashcat.rum.trace.api.span.DatadogSpanBuilder
import com.flashcat.rum.trace.internal.RumContextPropagator.Companion.DATADOG_INITIAL_CONTEXT
import com.flashcat.rum.trace.internal.RumContextPropagator.Companion.ERROR_FUTURE_GET_FAILED
import com.flashcat.rum.trace.internal.RumContextPropagator.Companion.INITIAL_DATADOG_CONTEXT_NOT_AVAILABLE_ERROR
import com.flashcat.rum.trace.internal.RumContextPropagator.Companion.extractRumContext
import com.flashcat.rum.trace.internal.RumContextPropagator.Companion.injectRumContext
import com.flashcat.rum.trace.utils.RumContextTestsUtils.RUM_CONTEXT_ACTION_ID
import com.flashcat.rum.trace.utils.RumContextTestsUtils.RUM_CONTEXT_APPLICATION_ID
import com.flashcat.rum.trace.utils.RumContextTestsUtils.RUM_CONTEXT_SESSION_ID
import com.flashcat.rum.trace.utils.RumContextTestsUtils.RUM_CONTEXT_VIEW_ID
import com.flashcat.rum.trace.utils.RumContextTestsUtils.aFlashcatContextWithRumContext
import com.flashcat.rum.trace.utils.RumContextTestsUtils.aRumContext
import com.flashcat.rum.trace.utils.verifyLog
import com.flashcat.rum.utils.forge.Configurator
import com.datadog.tools.unit.completedFutureMock
import com.datadog.tools.unit.completedWithErrorFutureMock
import com.datadog.tools.unit.forge.anException
import com.datadog.tools.unit.incompleteFutureMock
import com.datadog.trace.core.DDSpan
import com.datadog.trace.core.propagation.HttpCodec
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.quality.Strictness
import org.mockito.verification.VerificationMode
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
class RumContextPropagatorTest {

    private lateinit var mockRumFeatureScope: FeatureScope

    @Mock
    private lateinit var mockSpanBuilder: DatadogSpanBuilder

    @Mock
    private lateinit var mockInternalLogger: InternalLogger

    private lateinit var fakeUserInfo: UserInfo
    private var fakeAccountInfo: AccountInfo? = null

    @Forgery
    private lateinit var fakeFlashcatContext: FlashcatContext
    private lateinit var fakeRumContext: Map<String, Any?>

    private val mockSdkCore = mock<InternalSdkCore> {
        on { getFeature(Feature.RUM_FEATURE_NAME) } doAnswer { mockRumFeatureScope }
        on { internalLogger } doAnswer { mockInternalLogger }
    }

    private val testedRumContextPropagator = RumContextPropagator { mockSdkCore }

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeAccountInfo = forge.aNullable { AccountInfo(id = forge.aString()) }
        fakeUserInfo = UserInfo(id = forge.aString())
        mockRumFeatureScope = SdkFeatureMock.create()
        fakeRumContext = forge.aRumContext()
        fakeFlashcatContext = forge.aFlashcatContextWithRumContext(fakeRumContext, fakeAccountInfo, fakeUserInfo)
    }

    @Test
    fun `M get(Long, TimeUnit) W extractRumContext {DDSpan, block=True}`() {
        // Given
        val futureMock = incompleteFutureMock<FlashcatContext>()
        val span = newDDSpanWithLazyFlashcatContext(futureMock)

        // When
        span.extractRumContext(testedRumContextPropagator, block = true)

        // Then
        verify(futureMock).get(1, TimeUnit.SECONDS)
    }

    @Test
    fun `M get(Long, TimeUnit) W extractRumContext {DatadogSpan, block=True}`() {
        // Given
        val futureMock = incompleteFutureMock<FlashcatContext>()
        val span = newDatadogSpanWithLazyFlashcatContext(futureMock)

        // When
        span.extractRumContext(testedRumContextPropagator, block = true)

        // Then
        verify(futureMock).get(1, TimeUnit.SECONDS)
    }

    @Test
    fun `M not block W extractRumContext {DDSpan, isDone=true, block=False}`() {
        // Given
        val futureMock = completedFutureMock<FlashcatContext?>(null)
        val span = newDDSpanWithLazyFlashcatContext(futureMock)

        // When
        span.extractRumContext(mock(), block = false)

        // Then
        inOrder(futureMock) {
            verify(futureMock).isDone
            verify(futureMock).get()
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `M not block W extractRumContext {DatadogSpan, isDone=true, block=False}`() {
        // Given
        val futureMock = completedFutureMock<FlashcatContext?>(null)
        val span = newDatadogSpanWithLazyFlashcatContext(futureMock)

        // When
        span.extractRumContext(mock(), block = false)

        // Then
        inOrder(futureMock) {
            verify(futureMock).isDone
            verify(futureMock).get()
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `M not block W extractRumContext {DDSpan, isDone=false, block=False}`() {
        // Given
        val futureMock = incompleteFutureMock<FlashcatContext>()
        val span = newDDSpanWithLazyFlashcatContext(futureMock)

        // When
        span.extractRumContext(testedRumContextPropagator, block = false)

        // Then
        inOrder(futureMock) {
            verify(futureMock).isDone
            verify(futureMock, never()).get()
            verifyNoMoreInteractions()
        }
        mockInternalLogger.verifyErrorLogged(INITIAL_DATADOG_CONTEXT_NOT_AVAILABLE_ERROR)
        verifyRumContextNotExtracted(span)
    }

    @Test
    fun `M not block W extractRumContext {DatadogSpan, isDone=false, block=False}`() {
        // Given
        val futureMock = incompleteFutureMock<FlashcatContext>()
        val span = newDatadogSpanWithLazyFlashcatContext(futureMock)

        // When
        span.extractRumContext(testedRumContextPropagator, block = false)

        // Then
        inOrder(futureMock) {
            verify(futureMock).isDone
            verify(futureMock, never()).get()
            verifyNoMoreInteractions()
        }
        mockInternalLogger.verifyErrorLogged(INITIAL_DATADOG_CONTEXT_NOT_AVAILABLE_ERROR)
        verifyRumContextNotExtracted(span)
    }

    @Test
    fun `M log ERROR_FUTURE_GET_FAILED if Future#get(Long, TimeUnit) failed { DDSpan, block = true }`(forge: Forge) {
        // Given
        val span = newDDSpanWithLazyFlashcatContext(
            completedWithErrorFutureMock(forge.anException())
        )

        // When
        span.extractRumContext(testedRumContextPropagator, block = true)

        // Then
        mockInternalLogger.verifyErrorLogged(ERROR_FUTURE_GET_FAILED, mode = atLeastOnce())
        verifyRumContextNotExtracted(span)
    }

    @Test
    fun `M log ERROR_FUTURE_GET_FAILED if Future#get(Long, TimeUnit) failed { DatadogSpan, block = true }`(
        forge: Forge
    ) {
        // Given
        val span = newDatadogSpanWithLazyFlashcatContext(
            completedWithErrorFutureMock(forge.anException())
        )

        // When
        span.extractRumContext(testedRumContextPropagator, block = true)

        // Then
        mockInternalLogger.verifyErrorLogged(ERROR_FUTURE_GET_FAILED, mode = atLeastOnce())
        verifyRumContextNotExtracted(span)
    }

    @Test
    fun `M log ERROR_FUTURE_GET_FAILED if Future#get() failed { DDSpan, block = false }`(forge: Forge) {
        // Given
        val span = newDatadogSpanWithLazyFlashcatContext(
            completedWithErrorFutureMock(forge.anException())
        )

        // When
        span.extractRumContext(testedRumContextPropagator, block = false)

        // Then
        mockInternalLogger.verifyErrorLogged(ERROR_FUTURE_GET_FAILED, mode = atLeastOnce())
        verifyRumContextNotExtracted(span)
    }

    @Test
    fun `M log ERROR_FUTURE_GET_FAILED if Future#get() failed { DatadogSpan, block = false }`(forge: Forge) {
        // Given
        val span = newDatadogSpanWithLazyFlashcatContext(
            completedWithErrorFutureMock(forge.anException())
        )

        // When
        span.extractRumContext(testedRumContextPropagator, block = false)

        // Then
        mockInternalLogger.verifyErrorLogged(ERROR_FUTURE_GET_FAILED, mode = atLeastOnce())
    }

    @Test
    fun `M withTag(DATADOG_INITIAL_CONTEXT) W injectRumContextFeature`() {
        // Given
        val futureMock = completedFutureMock(fakeFlashcatContext)
        mockRumFeatureScope = SdkFeatureMock.create(futureMock)

        // When
        mockSpanBuilder.injectRumContext(testedRumContextPropagator)

        // Then
        argumentCaptor<Future<FlashcatContext>> {
            verify(mockSpanBuilder).withTag(eq(DATADOG_INITIAL_CONTEXT), capture())
            assertThat(firstValue).isEqualTo(futureMock)
        }
    }

    @Test
    fun `M set RUM tags W extractRumContext { DatadogSpan }`() {
        // Given
        val span = newDatadogSpanWithLazyFlashcatContext(
            completedFutureMock(fakeFlashcatContext)
        )

        // When
        span.extractRumContext(testedRumContextPropagator)

        // Then
        verify(span).getTag(DATADOG_INITIAL_CONTEXT)
        verify(span).setTag(LogAttributes.RUM_APPLICATION_ID, fakeRumContext[RUM_CONTEXT_APPLICATION_ID])
        verify(span).setTag(LogAttributes.RUM_SESSION_ID, fakeRumContext[RUM_CONTEXT_SESSION_ID])
        verify(span).setTag(LogAttributes.RUM_VIEW_ID, fakeRumContext[RUM_CONTEXT_VIEW_ID])
        verify(span).setTag(LogAttributes.RUM_ACTION_ID, fakeRumContext[RUM_CONTEXT_ACTION_ID])
        verify(span).setTag(HttpCodec.RUM_KEY_ACCOUNT_ID, fakeFlashcatContext.accountInfo?.id as? Any)
        verify(span).setTag(HttpCodec.RUM_KEY_USER_ID, fakeFlashcatContext.userInfo.id as? Any)
        verify(span).setTag(DATADOG_INITIAL_CONTEXT, null as Any?)

        verifyNoMoreInteractions(span)
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M set RUM tags W extractRumContext { DDSpan }`() {
        // Given
        val span = newDDSpanWithLazyFlashcatContext(
            completedFutureMock(fakeFlashcatContext)
        )

        // When
        span.extractRumContext(testedRumContextPropagator)

        // Then
        verify(span).getTag(DATADOG_INITIAL_CONTEXT)
        verify(span).setTag(LogAttributes.RUM_APPLICATION_ID, fakeRumContext[RUM_CONTEXT_APPLICATION_ID])
        verify(span).setTag(LogAttributes.RUM_SESSION_ID, fakeRumContext[RUM_CONTEXT_SESSION_ID])
        verify(span).setTag(LogAttributes.RUM_VIEW_ID, fakeRumContext[RUM_CONTEXT_VIEW_ID])
        verify(span).setTag(LogAttributes.RUM_ACTION_ID, fakeRumContext[RUM_CONTEXT_ACTION_ID])
        verify(span).setTag(HttpCodec.RUM_KEY_ACCOUNT_ID, fakeFlashcatContext.accountInfo?.id as? Any)
        verify(span).setTag(HttpCodec.RUM_KEY_USER_ID, fakeFlashcatContext.userInfo.id as? Any)
        verify(span).setTag(DATADOG_INITIAL_CONTEXT, null as Any?)

        verifyNoMoreInteractions(span)
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M set null RUM tags W extractRumContext { DatadogSpan, rum context is empty }`(forge: Forge) {
        // Given
        val futureMock = completedFutureMock(forge.aFlashcatContextWithRumContext(emptyMap()))
        val span = newDatadogSpanWithLazyFlashcatContext(futureMock)
        // When
        span.extractRumContext(testedRumContextPropagator)

        // Then
        verify(span).getTag(DATADOG_INITIAL_CONTEXT)
        verify(span).setTag(LogAttributes.RUM_APPLICATION_ID, null as Any?)
        verify(span).setTag(LogAttributes.RUM_SESSION_ID, null as Any?)
        verify(span).setTag(LogAttributes.RUM_VIEW_ID, null as Any?)
        verify(span).setTag(LogAttributes.RUM_ACTION_ID, null as Any?)
        verify(span).setTag(HttpCodec.RUM_KEY_ACCOUNT_ID, null as Any?)
        verify(span).setTag(HttpCodec.RUM_KEY_USER_ID, null as Any?)
        verify(span).setTag(DATADOG_INITIAL_CONTEXT, null as Any?)

        verifyNoMoreInteractions(span)
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M set null RUM tags W extractRumContext { DDSpan, rum context is empty }`(forge: Forge) {
        // Given
        val futureMock = completedFutureMock(forge.aFlashcatContextWithRumContext(emptyMap()))
        val span = newDDSpanWithLazyFlashcatContext(futureMock)

        // When
        span.extractRumContext(testedRumContextPropagator)

        // Then
        verify(span).getTag(DATADOG_INITIAL_CONTEXT)
        verify(span).setTag(LogAttributes.RUM_APPLICATION_ID, null as Any?)
        verify(span).setTag(LogAttributes.RUM_SESSION_ID, null as Any?)
        verify(span).setTag(LogAttributes.RUM_VIEW_ID, null as Any?)
        verify(span).setTag(LogAttributes.RUM_ACTION_ID, null as Any?)
        verify(span).setTag(HttpCodec.RUM_KEY_ACCOUNT_ID, null as Any?)
        verify(span).setTag(HttpCodec.RUM_KEY_USER_ID, null as Any?)
        verify(span).setTag(DATADOG_INITIAL_CONTEXT, null as Any?)

        verifyNoMoreInteractions(span)
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M not call setTag W getTag(DATADOG_INITIAL_CONTEXT) is null { DatadogSpan }`() {
        // Given
        val span = newDatadogSpanWithLazyFlashcatContext(null)

        // When
        span.extractRumContext(testedRumContextPropagator)

        // Then
        verify(span).getTag(DATADOG_INITIAL_CONTEXT)
        verifyNoMoreInteractions(span)
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M not call setTag W getTag(DATADOG_INITIAL_CONTEXT) is null { DDSpan }`() {
        // Given
        val span = newDDSpanWithLazyFlashcatContext(null)

        // When
        span.extractRumContext(testedRumContextPropagator)

        // Then
        verify(span).getTag(DATADOG_INITIAL_CONTEXT)
        verifyNoMoreInteractions(span)
        verifyNoInteractions(mockInternalLogger)
    }

    companion object {
        fun newDDSpanWithLazyFlashcatContext(value: Future<FlashcatContext?>?) = mock<DDSpan> {
            on { getTag(DATADOG_INITIAL_CONTEXT) } doAnswer { value }
        }

        fun newDatadogSpanWithLazyFlashcatContext(value: Future<FlashcatContext?>?) =
            mock<DatadogSpan> {
                on { getTag(DATADOG_INITIAL_CONTEXT) } doAnswer { value }
            }

        fun InternalLogger.verifyErrorLogged(message: String, mode: VerificationMode = times(1)) = verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            message,
            mode = mode
        )

        fun verifyRumContextNotExtracted(span: Any) {
            when (span) {
                is DDSpan -> {
                    verify(span).getTag(DATADOG_INITIAL_CONTEXT)
                    verify(span).setTag(DATADOG_INITIAL_CONTEXT, null as Any?)
                }

                is DatadogSpan -> {
                    verify(span).getTag(DATADOG_INITIAL_CONTEXT)
                    verify(span).setTag(DATADOG_INITIAL_CONTEXT, null as Any?)
                }
            }
            verifyNoMoreInteractions(span)
        }
    }
}
