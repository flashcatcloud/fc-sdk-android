/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.rum.integration

import cloud.flashcat.android.api.SdkCore
import cloud.flashcat.android.core.stub.StubSDKCore
import cloud.flashcat.android.rum.GlobalRumMonitor
import cloud.flashcat.android.rum.Rum
import cloud.flashcat.android.rum.RumConfiguration
import cloud.flashcat.android.rum.integration.tests.elmyr.RumIntegrationForgeConfigurator
import cloud.flashcat.android.rum.integration.tests.utils.MainLooperTestConfiguration
import cloud.flashcat.android.tests.assertj.StubEventsAssert.Companion.assertThat
import cloud.flashcat.tools.unit.annotations.TestConfigurationsProvider
import cloud.flashcat.tools.unit.extensions.TestConfigurationExtension
import cloud.flashcat.tools.unit.extensions.config.TestConfiguration
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
