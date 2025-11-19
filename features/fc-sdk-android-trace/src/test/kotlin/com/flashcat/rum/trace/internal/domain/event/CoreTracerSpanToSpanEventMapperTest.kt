/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.trace.internal.domain.event

import com.flashcat.rum.api.context.FlashcatContext
import com.flashcat.rum.log.LogAttributes
import com.flashcat.rum.trace.assertj.SpanEventAssert.Companion.assertThat
import com.flashcat.rum.utils.forge.Configurator
import com.datadog.trace.api.DDSpanId
import com.datadog.trace.api.internal.util.LongStringUtils
import com.datadog.trace.core.DDSpan
import com.datadog.trace.core.DDSpanContext
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class CoreTracerSpanToSpanEventMapperTest {

    private lateinit var testedMapper: CoreTracerSpanToSpanEventMapper

    @Forgery
    lateinit var fakeFlashcatContext: FlashcatContext

    @BoolForgery
    var fakeNetworkInfoEnabled: Boolean = false

    // region Tests

    @BeforeEach
    fun `set up`() {
        testedMapper = CoreTracerSpanToSpanEventMapper(fakeNetworkInfoEnabled)
    }

    @Test
    fun `M map a DdSpan to a SpanEvent W map()`(
        @Forgery fakeSpan: DDSpan
    ) {
        // Given
        val expectedMeta = fakeSpan.baggage + fakeSpan.tags.map {
            it.key to it.value.toString()
        }
        val expectedMetrics = fakeSpan.expectedMetrics()

        // When
        val event = testedMapper.map(fakeFlashcatContext, fakeSpan)

        // Then
        assertThat(event)
            .hasSpanId(DDSpanId.toHexStringPadded(fakeSpan.spanId))
            .hasLeastSignificant64BitsTraceId(LongStringUtils.toHexStringPadded(fakeSpan.traceId.toLong(), 16))
            .hasMostSignificant64BitsTraceId(LongStringUtils.toHexStringPadded(fakeSpan.traceId.toHighOrderLong(), 16))
            .hasParentId(DDSpanId.toHexStringPadded(fakeSpan.parentId))
            .hasServiceName(fakeSpan.serviceName)
            .hasOperationName(fakeSpan.operationName.toString())
            .hasResourceName(fakeSpan.resourceName.toString())
            .hasSpanType("custom")
            .hasSpanSource(fakeFlashcatContext.source)
            .hasApplicationId(null)
            .hasSessionId(null)
            .hasViewId(null)
            .hasErrorFlag(fakeSpan.error.toLong())
            .hasSpanStartTime(fakeSpan.startTime + fakeFlashcatContext.time.serverTimeOffsetNs)
            .hasSpanDuration(fakeSpan.durationNano)
            .hasSpanLinks(fakeSpan.links)
            .hasTracerVersion(fakeFlashcatContext.sdkVersion)
            .hasClientPackageVersion(fakeFlashcatContext.version).apply {
                if (fakeNetworkInfoEnabled) {
                    hasNetworkInfo(fakeFlashcatContext.networkInfo)
                } else {
                    doesntHaveNetworkInfo()
                }
            }
            .hasDeviceInfo(fakeFlashcatContext.deviceInfo)
            .hasOsInfo(fakeFlashcatContext.deviceInfo)
            .hasUserInfo(fakeFlashcatContext.userInfo)
            .hasAccountInfo(fakeFlashcatContext.accountInfo)
            .hasVariant(fakeFlashcatContext.variant)
            .hasMeta(expectedMeta)
            .hasMetrics(expectedMetrics)
    }

    @Test
    fun `M map a DdSpan to a SpanEvent with RUM info W map() {RUM info present}`(
        @Forgery fakeSpan: DDSpan,
        @StringForgery fakeApplicationId: String,
        @StringForgery fakeSessionId: String,
        @StringForgery fakeViewId: String
    ) {
        // Given
        val tags = fakeSpan.tags.toMutableMap().apply {
            this[LogAttributes.RUM_APPLICATION_ID] = fakeApplicationId
            this[LogAttributes.RUM_SESSION_ID] = fakeSessionId
            this[LogAttributes.RUM_VIEW_ID] = fakeViewId
        }
        whenever(fakeSpan.tags).thenReturn(tags)
        whenever(fakeSpan.context().tags).thenReturn(tags)

        // Given
        val expectedMeta = fakeSpan.baggage + fakeSpan.tags.map {
            it.key to it.value.toString()
        }
        val expectedMetrics = fakeSpan.expectedMetrics()

        // When
        val event = testedMapper.map(fakeFlashcatContext, fakeSpan)

        // Then
        assertThat(event)
            .hasSpanId(DDSpanId.toHexStringPadded(fakeSpan.spanId))
            .hasLeastSignificant64BitsTraceId(LongStringUtils.toHexStringPadded(fakeSpan.traceId.toLong(), 16))
            .hasMostSignificant64BitsTraceId(LongStringUtils.toHexStringPadded(fakeSpan.traceId.toHighOrderLong(), 16))
            .hasParentId(DDSpanId.toHexStringPadded(fakeSpan.parentId))
            .hasServiceName(fakeSpan.serviceName)
            .hasOperationName(fakeSpan.operationName.toString())
            .hasResourceName(fakeSpan.resourceName.toString())
            .hasSpanType("custom")
            .hasSpanSource(fakeFlashcatContext.source)
            .hasApplicationId(fakeApplicationId)
            .hasSessionId(fakeSessionId)
            .hasViewId(fakeViewId)
            .hasErrorFlag(fakeSpan.error.toLong())
            .hasSpanStartTime(fakeSpan.startTime + fakeFlashcatContext.time.serverTimeOffsetNs)
            .hasSpanDuration(fakeSpan.durationNano)
            .hasSpanLinks(fakeSpan.links)
            .hasTracerVersion(fakeFlashcatContext.sdkVersion)
            .hasClientPackageVersion(fakeFlashcatContext.version).apply {
                if (fakeNetworkInfoEnabled) {
                    hasNetworkInfo(fakeFlashcatContext.networkInfo)
                } else {
                    doesntHaveNetworkInfo()
                }
            }.hasUserInfo(fakeFlashcatContext.userInfo)
            .hasAccountInfo(fakeFlashcatContext.accountInfo)
            .hasDeviceInfo(fakeFlashcatContext.deviceInfo)
            .hasOsInfo(fakeFlashcatContext.deviceInfo)
            .hasVariant(fakeFlashcatContext.variant)
            .hasMeta(expectedMeta)
            .hasMetrics(expectedMetrics)
    }

    @Test
    fun `M mark the SpanEvent as top span W map() { parentId is 0 }`(
        @Forgery fakeSpan: DDSpan
    ) {
        // Given
        whenever(fakeSpan.parentId).thenReturn(0L)

        // When
        val event = testedMapper.map(fakeFlashcatContext, fakeSpan)

        // Then
        assertThat(event).isTopSpan()
    }

    @Test
    fun `M not mark the SpanEvent as top span W map() { parentId is different than 0 }`(
        forge: Forge,
        @Forgery fakeSpan: DDSpan
    ) {
        // Given
        whenever(fakeSpan.parentId).thenReturn(forge.aLong(min = 1))

        // When
        val event = testedMapper.map(fakeFlashcatContext, fakeSpan)

        // Then
        assertThat(event).isNotTopSpan()
    }

    // endregion

    // region Internal

    private fun DDSpan.expectedMetrics(): Map<String, Number> {
        return tags.filterValues { it is Number }.mapValues { it.value as Number }.toMutableMap().apply {
            this[DDSpanContext.PRIORITY_SAMPLING_KEY] = spanSamplingPriority
        }
    }

    // endregion
}
