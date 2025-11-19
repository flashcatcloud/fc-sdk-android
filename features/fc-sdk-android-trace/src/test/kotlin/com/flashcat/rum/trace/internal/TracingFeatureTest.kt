/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.trace.internal

import com.flashcat.rum.api.InternalLogger
import com.flashcat.rum.api.feature.Feature
import com.flashcat.rum.api.feature.FeatureSdkCore
import com.flashcat.rum.api.storage.FeatureStorageConfiguration
import com.flashcat.rum.trace.event.SpanEventMapper
import com.flashcat.rum.trace.internal.data.CoreTraceWriter
import com.flashcat.rum.trace.internal.domain.event.CoreTracerSpanToSpanEventMapper
import com.flashcat.rum.trace.internal.domain.event.SpanEventMapperWrapper
import com.flashcat.rum.trace.internal.net.TracesRequestFactory
import com.flashcat.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.StringForgery
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class TracingFeatureTest {

    private lateinit var testedFeature: TracingFeature

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockSpanEventMapper: SpanEventMapper

    @StringForgery(regex = "https://[a-z]+\\.com")
    lateinit var fakeEndpointUrl: String

    @BoolForgery
    var fakeNetworkInfoEnabled: Boolean = false

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger

        testedFeature = TracingFeature(
            mockSdkCore,
            fakeEndpointUrl,
            mockSpanEventMapper,
            fakeNetworkInfoEnabled
        )
    }

    @Test
    fun `M initialize core writer W initialize()`() {
        // When
        testedFeature.onInitialize(mock())

        // Then
        val traceWriter = testedFeature.coreTracerDataWriter as CoreTraceWriter
        val ddSpanToSpanEventMapper = traceWriter.ddSpanToSpanEventMapper
        assertThat(ddSpanToSpanEventMapper).isInstanceOf(CoreTracerSpanToSpanEventMapper::class.java)
        assertThat((ddSpanToSpanEventMapper as CoreTracerSpanToSpanEventMapper).networkInfoEnabled)
            .isEqualTo(fakeNetworkInfoEnabled)
    }

    @Test
    fun `M use the eventMapper for core writer W initialize()`() {
        // When
        testedFeature.onInitialize(mock())

        // Then
        val dataWriter = testedFeature.coreTracerDataWriter as? CoreTraceWriter
        val spanEventMapperWrapper = dataWriter?.eventMapper as? SpanEventMapperWrapper
        val spanEventMapper = spanEventMapperWrapper?.wrappedEventMapper
        assertThat(spanEventMapper).isSameAs(mockSpanEventMapper)
    }

    @Test
    fun `M provide tracing feature name W name()`() {
        // When+Then
        assertThat(testedFeature.name)
            .isEqualTo(Feature.TRACING_FEATURE_NAME)
    }

    @Test
    fun `M provide tracing request factory W requestFactory()`() {
        // Given
        testedFeature.onInitialize(mock())

        // When+Then
        assertThat(testedFeature.requestFactory)
            .isInstanceOf(TracesRequestFactory::class.java)
    }

    @Test
    fun `M provide default storage configuration W storageConfiguration()`() {
        // When+Then
        assertThat(testedFeature.storageConfiguration)
            .isEqualTo(FeatureStorageConfiguration.DEFAULT)
    }
}
