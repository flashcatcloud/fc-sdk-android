/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.trace.integration.otel

import com.flashcat.rum.api.feature.Feature
import com.flashcat.rum.core.stub.StubSDKCore
import com.flashcat.rum.trace.Trace
import com.flashcat.rum.trace.TraceConfiguration
import com.flashcat.rum.trace.event.SpanEventMapper
import com.flashcat.rum.trace.integration.tests.assertj.SpansPayloadAssert
import com.flashcat.rum.trace.integration.tests.elmyr.TraceIntegrationForgeConfigurator
import com.flashcat.rum.trace.model.SpanEvent
import com.flashcat.rum.trace.opentelemetry.OtelTracerProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.util.concurrent.TimeUnit
import kotlin.system.measureNanoTime

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@ForgeConfiguration(TraceIntegrationForgeConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OtelTraceConfigurationTest {

    private lateinit var stubSdkCore: StubSDKCore

    @BeforeEach
    fun `set up`(forge: Forge) {
        stubSdkCore = StubSDKCore(forge)
    }

    @RepeatedTest(10)
    fun `M send span without network info W setNetworkInfoEnabled(false) + buildSpan() + start() + finish()`(
        @StringForgery fakeInstrumentationName: String,
        @StringForgery fakeOperation: String
    ) {
        // Given
        Trace.enable(
            sdkCore = stubSdkCore,
            traceConfiguration = TraceConfiguration.Builder().setNetworkInfoEnabled(false).build()
        )
        val blockingWriterWrapper = checkNotNull(stubSdkCore.getFeature(Feature.TRACING_FEATURE_NAME))
            .useBlockingWriter()
        val testedProvider = OtelTracerProvider.Builder(stubSdkCore).build()
        val tracer = testedProvider.tracerBuilder(fakeInstrumentationName).build()

        // When
        var leastSignificantTraceId: String
        var mostSignificantTraceId: String
        var spanId: String
        val fullDuration = measureNanoTime {
            val otelSpan = tracer.spanBuilder(fakeOperation).startSpan()
            leastSignificantTraceId = otelSpan.leastSignificant64BitsTraceIdAsHex()
            mostSignificantTraceId = otelSpan.mostSignificant64BitsTraceIdAsHex()
            spanId = otelSpan.spanIdAsHex()
            Thread.sleep(OP_DURATION_MS)
            otelSpan.end()
        }

        // Then
        blockingWriterWrapper.waitForTracesMax(1)
        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val payload0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        SpansPayloadAssert.assertThat(payload0)
            .hasEnv(stubSdkCore.getFlashcatContext().env)
            .hasSpanAtIndexWith(0) {
                hasLeastSignificant64BitsTraceId(leastSignificantTraceId)
                hasMostSignificant64BitsTraceId(mostSignificantTraceId)
                hasSpanId(spanId)
                hasService(stubSdkCore.getFlashcatContext().service)
                hasVersion(stubSdkCore.getFlashcatContext().version)
                hasSource(stubSdkCore.getFlashcatContext().source)
                hasTracerVersion(stubSdkCore.getFlashcatContext().sdkVersion)
                hasError(0)
                hasName(DEFAULT_SPAN_NAME)
                hasResource(fakeOperation)
                hasDurationBetween(OP_DURATION_NS, fullDuration)
                doesNotHaveConnectivity()
                doesNotHaveSimCarrierId()
                doesNotHaveSimCarrierName()
            }
    }

    @RepeatedTest(10)
    fun `M send span with network info W setNetworkInfoEnabled(true) + buildSpan() + start() + finish()`(
        @StringForgery fakeInstrumentationName: String,
        @StringForgery fakeOperation: String
    ) {
        // Given
        Trace.enable(
            sdkCore = stubSdkCore,
            traceConfiguration = TraceConfiguration.Builder().setNetworkInfoEnabled(true).build()
        )
        val blockingWriterWrapper = checkNotNull(stubSdkCore.getFeature(Feature.TRACING_FEATURE_NAME))
            .useBlockingWriter()
        val testedProvider = OtelTracerProvider.Builder(stubSdkCore).build()
        val tracer = testedProvider.tracerBuilder(fakeInstrumentationName).build()

        // When
        var leastSignificantTraceId: String
        var mostSignificantTraceId: String
        var spanId: String
        val fullDuration = measureNanoTime {
            val otelSpan = tracer.spanBuilder(fakeOperation).startSpan()
            leastSignificantTraceId = otelSpan.leastSignificant64BitsTraceIdAsHex()
            mostSignificantTraceId = otelSpan.mostSignificant64BitsTraceIdAsHex()
            spanId = otelSpan.spanIdAsHex()
            Thread.sleep(OP_DURATION_MS)
            otelSpan.end()
        }

        // Then
        blockingWriterWrapper.waitForTracesMax(1)
        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val payload0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        SpansPayloadAssert.assertThat(payload0)
            .hasEnv(stubSdkCore.getFlashcatContext().env)
            .hasSpanAtIndexWith(0) {
                hasLeastSignificant64BitsTraceId(leastSignificantTraceId)
                hasMostSignificant64BitsTraceId(mostSignificantTraceId)
                hasSpanId(spanId)
                hasService(stubSdkCore.getFlashcatContext().service)
                hasVersion(stubSdkCore.getFlashcatContext().version)
                hasSource(stubSdkCore.getFlashcatContext().source)
                hasTracerVersion(stubSdkCore.getFlashcatContext().sdkVersion)
                hasError(0)
                hasName(DEFAULT_SPAN_NAME)
                hasResource(fakeOperation)
                hasDurationBetween(OP_DURATION_NS, fullDuration)
                hasConnectivity(stubSdkCore.getFlashcatContext().networkInfo.connectivity.name)
                hasSimCarrierId(stubSdkCore.getFlashcatContext().networkInfo.carrierId)
                hasSimCarrierName(stubSdkCore.getFlashcatContext().networkInfo.carrierName)
            }
    }

    @RepeatedTest(10)
    fun `M send mapped span W setEventMapper() + buildSpan() + start() + finish()`(
        @StringForgery fakeInstrumentationName: String,
        @StringForgery fakeOperation: String,
        @StringForgery fakeMappedOperation: String,
        @StringForgery fakeMappedResource: String
    ) {
        // Given
        val stubMapper = object : SpanEventMapper {
            override fun map(event: SpanEvent): SpanEvent {
                event.name = fakeMappedOperation
                event.resource = fakeMappedResource
                return event
            }
        }
        Trace.enable(
            sdkCore = stubSdkCore,
            traceConfiguration = TraceConfiguration.Builder().setEventMapper(stubMapper).build()
        )
        val blockingWriterWrapper = checkNotNull(
            stubSdkCore.getFeature(Feature.TRACING_FEATURE_NAME)
        ).useBlockingWriter()
        val testedProvider = OtelTracerProvider.Builder(stubSdkCore).build()
        val tracer = testedProvider.tracerBuilder(fakeInstrumentationName).build()

        // When
        var leastSignificantTraceId: String
        var mostSignificantTraceId: String
        var spanId: String
        val fullDuration = measureNanoTime {
            val span = tracer.spanBuilder(fakeOperation).startSpan()
            leastSignificantTraceId = span.leastSignificant64BitsTraceIdAsHex()
            mostSignificantTraceId = span.mostSignificant64BitsTraceIdAsHex()
            spanId = span.spanIdAsHex()
            Thread.sleep(OP_DURATION_MS)
            span.end()
        }

        // Then
        blockingWriterWrapper.waitForTracesMax(1)
        val eventsWritten = stubSdkCore.eventsWritten(Feature.TRACING_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(1)
        val payload0 = JsonParser.parseString(eventsWritten[0].eventData) as JsonObject
        SpansPayloadAssert.assertThat(payload0)
            .hasEnv(stubSdkCore.getFlashcatContext().env)
            .hasSpanAtIndexWith(0) {
                hasLeastSignificant64BitsTraceId(leastSignificantTraceId)
                hasMostSignificant64BitsTraceId(mostSignificantTraceId)
                hasSpanId(spanId)
                hasService(stubSdkCore.getFlashcatContext().service)
                hasVersion(stubSdkCore.getFlashcatContext().version)
                hasSource(stubSdkCore.getFlashcatContext().source)
                hasTracerVersion(stubSdkCore.getFlashcatContext().sdkVersion)
                hasError(0)
                hasName(fakeMappedOperation)
                hasResource(fakeMappedResource)
                hasDurationBetween(OP_DURATION_NS, fullDuration)
            }
    }

    companion object {
        private const val OP_DURATION_MS = 10L
        private val OP_DURATION_NS = TimeUnit.MILLISECONDS.toNanos(OP_DURATION_MS)
        private const val DEFAULT_SPAN_NAME = "internal"
    }
}
