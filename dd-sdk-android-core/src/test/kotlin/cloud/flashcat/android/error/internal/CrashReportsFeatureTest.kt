/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.error.internal

import android.app.Application
import cloud.flashcat.android.api.feature.FeatureSdkCore
import cloud.flashcat.android.utils.config.ApplicationContextTestConfiguration
import cloud.flashcat.android.utils.forge.Configurator
import cloud.flashcat.tools.unit.annotations.TestConfigurationsProvider
import cloud.flashcat.tools.unit.extensions.TestConfigurationExtension
import cloud.flashcat.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class CrashReportsFeatureTest {

    private lateinit var testedFeature: CrashReportsFeature

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    var jvmExceptionHandler: Thread.UncaughtExceptionHandler? = null

    @BeforeEach
    fun `set up crash reports`() {
        testedFeature = CrashReportsFeature(mockSdkCore)
        jvmExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
    }

    @AfterEach
    fun `tear down crash reports`() {
        Thread.setDefaultUncaughtExceptionHandler(jvmExceptionHandler)
        testedFeature.originalUncaughtExceptionHandler = jvmExceptionHandler
    }

    @Test
    fun `M register crash handler W initialize`() {
        // When
        testedFeature.onInitialize(appContext.mockInstance)

        // Then
        val handler = Thread.getDefaultUncaughtExceptionHandler()
        assertThat(handler)
            .isInstanceOf(DatadogExceptionHandler::class.java)
    }

    @Test
    fun `M restore original crash handler W onStop()`() {
        // Given
        val mockOriginalHandler: Thread.UncaughtExceptionHandler = mock()
        Thread.setDefaultUncaughtExceptionHandler(mockOriginalHandler)

        // When
        testedFeature.onInitialize(appContext.mockInstance)
        testedFeature.onStop()

        // Then
        val finalHandler = Thread.getDefaultUncaughtExceptionHandler()
        assertThat(finalHandler).isSameAs(mockOriginalHandler)
    }

    companion object {
        val appContext = ApplicationContextTestConfiguration(Application::class.java)

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(appContext)
        }
    }
}
