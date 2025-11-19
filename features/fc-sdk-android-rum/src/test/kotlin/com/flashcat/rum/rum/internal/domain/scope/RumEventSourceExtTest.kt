/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.rum.internal.domain.scope

import com.flashcat.rum.api.InternalLogger
import com.flashcat.rum.rum.model.ActionEvent
import com.flashcat.rum.rum.model.ErrorEvent
import com.flashcat.rum.rum.model.LongTaskEvent
import com.flashcat.rum.rum.model.ResourceEvent
import com.flashcat.rum.rum.model.ViewEvent
import com.flashcat.rum.rum.utils.forge.Configurator
import com.flashcat.rum.rum.utils.forge.aStringNotMatchingSet
import com.flashcat.rum.rum.utils.verifyLog
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import java.util.Locale

@Extensions(
    ExtendWith(ForgeExtension::class),
    ExtendWith(MockitoExtension::class)
)
@ForgeConfiguration(Configurator::class)
internal class RumEventSourceExtTest {

    private lateinit var fakeInvalidSource: String
    private lateinit var fakeValidRumSource: String

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @BeforeEach
    fun `set up`(forge: Forge) {
        // we are using the ViewEvent.ViewEventSource here as the source enum for all the events is
        // generated from the same _common-schema.json
        fakeInvalidSource = forge.aStringNotMatchingSet(
            ViewEvent.ViewEventSource.values()
                .map {
                    it.toJson().asString
                }.toSet()
        )
        fakeValidRumSource = forge.aValueFrom(ViewEvent.ViewEventSource::class.java).toJson().asString
    }

    // region ViewEvent

    @Test
    fun `M resolve the ViewEvent source W viewEventSource`() {
        // When
        val source = ViewEvent.ViewEventSource.tryFromSource(fakeValidRumSource, mockInternalLogger)
            ?.toJson()?.asString

        // Then
        assertThat(source)
            .isEqualTo(fakeValidRumSource)
    }

    @Test
    fun `M return null W viewEventSource { unknown source }`() {
        assertThat(ViewEvent.ViewEventSource.tryFromSource(fakeInvalidSource, mockInternalLogger)).isNull()
    }

    @Test
    fun `M send an error dev log W viewEventSource { unknown source }`() {
        // When
        ViewEvent.ViewEventSource.tryFromSource(fakeInvalidSource, mockInternalLogger)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            UNKNOWN_SOURCE_WARNING_MESSAGE_FORMAT.format(Locale.US, fakeInvalidSource),
            NoSuchElementException::class.java
        )
    }

    // endregion

    // region ActionEvent

    @Test
    fun `M resolve the Action source W actionEventSource`() {
        // When
        val source = ActionEvent.ActionEventSource.tryFromSource(fakeValidRumSource, mockInternalLogger)
            ?.toJson()?.asString

        // Then
        assertThat(source).isEqualTo(fakeValidRumSource)
    }

    @Test
    fun `M return null W actionEventSource { unknown source }`() {
        assertThat(ActionEvent.ActionEventSource.tryFromSource(fakeInvalidSource, mockInternalLogger)).isNull()
    }

    @Test
    fun `M send an error dev log W actionEventSource { unknown source }`() {
        // When
        ActionEvent.ActionEventSource.tryFromSource(fakeInvalidSource, mockInternalLogger)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            UNKNOWN_SOURCE_WARNING_MESSAGE_FORMAT.format(Locale.US, fakeInvalidSource),
            NoSuchElementException::class.java
        )
    }

    // endregion

    // region ErrorEvent

    @Test
    fun `M resolve the ErrorEvent source W errorEventSource`() {
        // When
        val source = ErrorEvent.ErrorEventSource
            .tryFromSource(fakeValidRumSource, mockInternalLogger)?.toJson()?.asString

        // Then
        assertThat(source).isEqualTo(fakeValidRumSource)
    }

    @Test
    fun `M return null W errorEventSource { unknown source }`() {
        assertThat(
            ErrorEvent.ErrorEventSource.tryFromSource(fakeInvalidSource, mockInternalLogger)
        ).isNull()
    }

    @Test
    fun `M send an error dev log W errorEventSource { unknown source }`() {
        // When
        ErrorEvent.ErrorEventSource.tryFromSource(fakeInvalidSource, mockInternalLogger)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            UNKNOWN_SOURCE_WARNING_MESSAGE_FORMAT.format(Locale.US, fakeInvalidSource),
            NoSuchElementException::class.java
        )
    }

    // endregion

    // region ResourceEvent

    @Test
    fun `M resolve the ResourceEvent source W resourceEventSource`() {
        // When
        val source = ResourceEvent.ResourceEventSource.tryFromSource(fakeValidRumSource, mockInternalLogger)
            ?.toJson()?.asString

        // Then
        assertThat(source).isEqualTo(fakeValidRumSource)
    }

    @Test
    fun `M return null W resourceEventSource { unknown source }`() {
        assertThat(
            ResourceEvent.ResourceEventSource.tryFromSource(fakeInvalidSource, mockInternalLogger)
        ).isNull()
    }

    @Test
    fun `M send an error dev log W resourceEventSource { unknown source }`() {
        // When
        ResourceEvent.ResourceEventSource.tryFromSource(fakeInvalidSource, mockInternalLogger)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            UNKNOWN_SOURCE_WARNING_MESSAGE_FORMAT.format(Locale.US, fakeInvalidSource),
            NoSuchElementException::class.java
        )
    }

    // endregion

    // region LongTaskEvent

    @Test
    fun `M resolve the LongTaskEvent source W longTaskEventSource`() {
        // When
        val source = LongTaskEvent.LongTaskEventSource.tryFromSource(fakeValidRumSource, mockInternalLogger)
            ?.toJson()?.asString

        // Then
        assertThat(source).isEqualTo(fakeValidRumSource)
    }

    @Test
    fun `M return null W longTaskEventSource { unknown source }`() {
        assertThat(LongTaskEvent.LongTaskEventSource.tryFromSource(fakeInvalidSource, mockInternalLogger))
            .isNull()
    }

    @Test
    fun `M send an error dev log W longTaskEventSource { unknown source }`() {
        // When
        LongTaskEvent.LongTaskEventSource.tryFromSource(fakeInvalidSource, mockInternalLogger)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            UNKNOWN_SOURCE_WARNING_MESSAGE_FORMAT.format(Locale.US, fakeInvalidSource),
            NoSuchElementException::class.java
        )
    }

    // endregion
}
