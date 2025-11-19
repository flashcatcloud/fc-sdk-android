/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.trace.internal.domain.event

import com.flashcat.rum.api.InternalLogger
import com.flashcat.rum.trace.event.SpanEventMapper
import com.flashcat.rum.trace.model.SpanEvent
import com.flashcat.rum.trace.utils.verifyLog
import com.flashcat.rum.utils.forge.Configurator
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Locale

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@ForgeConfiguration(Configurator::class)
internal class SpanEventMapperWrapperTest {

    lateinit var testedEventMapper: SpanEventMapperWrapper

    @Mock
    lateinit var mockWrappedEventMapper: SpanEventMapper

    @Mock
    lateinit var mockSpanEvent: SpanEvent

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @BeforeEach
    fun `set up`() {
        testedEventMapper = SpanEventMapperWrapper(mockWrappedEventMapper, mockInternalLogger)
    }

    @Test
    fun `M map and return the SpanEvent W map`() {
        // GIVEN
        whenever(mockWrappedEventMapper.map(mockSpanEvent)).thenReturn(mockSpanEvent)

        // WHEN
        val mappedEvent = testedEventMapper.map(mockSpanEvent)

        // THEN
        verify(mockWrappedEventMapper).map(mockSpanEvent)
        assertThat(mappedEvent).isEqualTo(mockSpanEvent)
    }

    @Test
    fun `M return null if the mapped returned event is not the same instance W map`() {
        // GIVEN
        whenever(mockWrappedEventMapper.map(mockSpanEvent)).thenReturn(mock())

        // WHEN
        val mappedEvent = testedEventMapper.map(mockSpanEvent)

        // THEN
        verify(mockWrappedEventMapper).map(mockSpanEvent)
        assertThat(mappedEvent).isNull()
    }

    @Test
    fun `M log a warning if the mapped returned event is not the same instance W map`() {
        // GIVEN
        whenever(mockWrappedEventMapper.map(mockSpanEvent)).thenReturn(mock())

        // WHEN
        testedEventMapper.map(mockSpanEvent)

        // THEN
        mockInternalLogger.verifyLog(
            InternalLogger.Level.INFO,
            InternalLogger.Target.USER,
            SpanEventMapperWrapper.NOT_SAME_EVENT_INSTANCE_WARNING_MESSAGE.format(
                Locale.US,
                mockSpanEvent.toString()
            )
        )
    }
}
