/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.flags

import com.flashcat.rum.FlashcatSite
import com.flashcat.rum.api.InternalLogger
import com.flashcat.rum.api.context.FlashcatContext
import com.flashcat.rum.api.feature.Feature.Companion.FLAGS_FEATURE_NAME
import com.flashcat.rum.api.feature.Feature.Companion.RUM_FEATURE_NAME
import com.flashcat.rum.core.InternalSdkCore
import com.flashcat.rum.flags.FlagsClient.Companion.FLAGS_CLIENT_EXECUTOR_NAME
import com.flashcat.rum.flags.internal.FlagsFeature
import com.flashcat.rum.flags.utils.forge.ForgeConfigurator
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.ExecutorService

@ExtendWith(MockitoExtension::class, ForgeExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class FlagsTest {

    @Mock
    lateinit var mockSdkCore: InternalSdkCore

    @Mock
    lateinit var mockExecutorService: ExecutorService

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockFlashcatContext: FlashcatContext

    @StringForgery
    lateinit var fakeClientToken: String

    @StringForgery
    lateinit var fakeEnv: String

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockSdkCore.createSingleThreadExecutorService(FLAGS_CLIENT_EXECUTOR_NAME)) doReturn
            mockExecutorService

        whenever(mockFlashcatContext.clientToken) doReturn fakeClientToken
        whenever(mockFlashcatContext.site) doReturn FlashcatSite.US1
        whenever(mockFlashcatContext.env) doReturn fakeEnv
        whenever(mockSdkCore.getFlashcatContext()) doReturn mockFlashcatContext
        whenever(mockSdkCore.getFeature(RUM_FEATURE_NAME)) doReturn mock()
    }

    // region enable()

    @Test
    fun `M register FlagsFeature W enable()`() {
        // Given
        val config = FlagsConfiguration.Builder().trackExposures(false).build()

        // When
        Flags.enable(config, mockSdkCore)

        // Then
        argumentCaptor<FlagsFeature> {
            verify(mockSdkCore).registerFeature(capture())
            assertThat(lastValue.name).isEqualTo(FLAGS_FEATURE_NAME)
        }
    }

    @Test
    fun `M use default configuration W enable() { no config provided }`() {
        // When
        Flags.enable(sdkCore = mockSdkCore)

        // Then
        argumentCaptor<FlagsFeature> {
            verify(mockSdkCore).registerFeature(capture())
            assertThat(lastValue.flagsConfiguration.trackExposures).isTrue()
            assertThat(lastValue.flagsConfiguration.customExposureEndpoint).isNull()
            assertThat(lastValue.flagsConfiguration).isEqualTo(FlagsConfiguration.default)
        }
    }

    @Test
    fun `M pass default configuration to FlagsFeature W enable() { default config }`() {
        // Given
        val defaultConfiguration = FlagsConfiguration.default

        // When
        Flags.enable(defaultConfiguration, mockSdkCore)

        // Then
        argumentCaptor<FlagsFeature> {
            verify(mockSdkCore).registerFeature(capture())
            assertThat(lastValue.flagsConfiguration.trackExposures).isTrue()
            assertThat(lastValue.flagsConfiguration.customExposureEndpoint).isNull()
            assertThat(lastValue.flagsConfiguration.customFlagEndpoint).isNull()
            assertThat(lastValue.flagsConfiguration).isEqualTo(FlagsConfiguration.default)
        }
    }

    @Test
    fun `M pass configuration to FlagsFeature W enable() { with custom config }`(
        @StringForgery(regex = "https://[a-z]+\\.com(/[a-z]+)+") fakeCustomEndpoint: String,
        @StringForgery(regex = "https://[a-z]+\\.com(/[a-z]+)+") fakeCustomFlagEndpoint: String
    ) {
        // Given
        val fakeConfiguration = FlagsConfiguration.Builder()
            .useCustomExposureEndpoint(fakeCustomEndpoint)
            .useCustomFlagEndpoint(fakeCustomFlagEndpoint)
            .build()

        // When
        Flags.enable(fakeConfiguration, mockSdkCore)

        // Then
        argumentCaptor<FlagsFeature> {
            verify(mockSdkCore).registerFeature(capture())
            assertThat(lastValue.name).isEqualTo(FLAGS_FEATURE_NAME)
            assertThat(lastValue.flagsConfiguration.customExposureEndpoint).isEqualTo(fakeCustomEndpoint)
            assertThat(lastValue.flagsConfiguration.customFlagEndpoint).isEqualTo(fakeCustomFlagEndpoint)
        }
    }

    // endregion
}
