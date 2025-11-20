/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.rum.integration

import com.flashcat.rum.api.SdkCore
import com.flashcat.rum.core.stub.StubSDKCore
import com.flashcat.rum.rum.GlobalRumMonitor
import com.flashcat.rum.rum.Rum
import com.flashcat.rum.rum.RumConfiguration
import com.flashcat.rum.rum.integration.tests.elmyr.RumIntegrationForgeConfigurator
import com.flashcat.rum.rum.integration.tests.utils.MainLooperTestConfiguration
import com.flashcat.rum.tests.assertj.StubEventsAssert.Companion.assertThat
import com.flashcat.tools.unit.annotations.TestConfigurationsProvider
import com.flashcat.tools.unit.extensions.TestConfigurationExtension
import com.flashcat.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@ForgeConfiguration(RumIntegrationForgeConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GlobalRumMonitorTest {

    private lateinit var stubSdkCore: StubSDKCore

    @StringForgery
    private lateinit var fakeApplicationId: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        stubSdkCore = StubSDKCore(forge)
        val fakeRumConfiguration = RumConfiguration.Builder(fakeApplicationId)
            .trackNonFatalAnrs(false)
            .build()
        Rum.enable(fakeRumConfiguration, stubSdkCore)
    }

    @Test
    fun `M return true W isRegistered()`() {
        // Given

        // When
        val isRegistered = GlobalRumMonitor.isRegistered(stubSdkCore)

        // Then
        assertThat(isRegistered).isTrue()
    }

    @Test
    fun `M return false W isRegistered() {sdkCore without RUM}`() {
        // Given
        val otherSdkCore: SdkCore = mock()

        // When
        val isRegistered = GlobalRumMonitor.isRegistered(otherSdkCore)

        // Then
        assertThat(isRegistered).isFalse()
    }

    companion object {
        private val mainLooper = MainLooperTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(mainLooper)
        }
    }
}
