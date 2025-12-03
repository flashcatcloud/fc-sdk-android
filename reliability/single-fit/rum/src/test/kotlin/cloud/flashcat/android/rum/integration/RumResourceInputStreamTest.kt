/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.rum.integration

import cloud.flashcat.android.api.feature.Feature
import cloud.flashcat.android.core.stub.StubSDKCore
import cloud.flashcat.android.rum.GlobalRumMonitor
import cloud.flashcat.android.rum.Rum
import cloud.flashcat.android.rum.RumConfiguration
import cloud.flashcat.android.rum.integration.tests.assertj.hasRumEvent
import cloud.flashcat.android.rum.integration.tests.elmyr.RumIntegrationForgeConfigurator
import cloud.flashcat.android.rum.integration.tests.utils.MainLooperTestConfiguration
import cloud.flashcat.android.rum.resource.RumResourceInputStream
import cloud.flashcat.android.tests.assertj.StubEventsAssert.Companion.assertThat
import cloud.flashcat.tools.unit.annotations.TestConfigurationsProvider
import cloud.flashcat.tools.unit.extensions.TestConfigurationExtension
import cloud.flashcat.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.ByteArrayOutputStream
import java.io.InputStream

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@ForgeConfiguration(RumIntegrationForgeConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RumResourceInputStreamTest {

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

    @RepeatedTest(4)
    fun `M report RUM Resource W asRumResource()`(
        @StringForgery viewKey: String,
        @StringForgery viewName: String,
        @StringForgery resourceUrl: String,
        @StringForgery data: String
    ) {
        // Given
        GlobalRumMonitor.get(stubSdkCore).startView(viewKey, viewName)
        val input = data.toByteArray()
        val inputStream = input.inputStream()
        val rumResourceInputStream = RumResourceInputStream(inputStream, resourceUrl, stubSdkCore)
        val outputStream = ByteArrayOutputStream(input.size)

        // When
        rumResourceInputStream.use {
            it.transferTo(outputStream)
        }

        // Then
        assertThat(outputStream.toByteArray()).isEqualTo(input)
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(3)
            .hasRumEvent(index = 0) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewName(viewName)
                hasResourceCount(0)
            }
            .hasRumEvent(index = 1) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("resource")
                hasViewName(viewName)
                hasResourceUrl(resourceUrl)
            }
            .hasRumEvent(index = 2) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewName(viewName)
                hasResourceCount(1)
            }
    }

    @RepeatedTest(4)
    fun `M report RUM Error W asRumResource() + read()`(
        @StringForgery viewKey: String,
        @StringForgery viewName: String,
        @StringForgery resourceUrl: String,
        @Forgery error: Throwable
    ) {
        // Given
        GlobalRumMonitor.get(stubSdkCore).startView(viewKey, viewName)
        val inputStream: InputStream = mock()
        val rumResourceInputStream = RumResourceInputStream(inputStream, resourceUrl, stubSdkCore)
        whenever(inputStream.read()) doThrow error

        // When
        var forwardedError: Throwable? = null
        try {
            rumResourceInputStream.read()
        } catch (e: Throwable) {
            forwardedError = e
        }

        // Then
        assertThat(forwardedError).isEqualTo(error)
        val eventsWritten = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(eventsWritten).hasSize(3)
            .hasRumEvent(index = 0) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewName(viewName)
                hasResourceCount(0)
                hasErrorCount(0)
            }
            .hasRumEvent(index = 1) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("error")
                hasViewName(viewName)
                hasErrorType(error.javaClass.name)
            }
            .hasRumEvent(index = 2) {
                hasService(stubSdkCore.getDatadogContext().service)
                hasApplicationId(fakeApplicationId)
                hasSessionType("user")
                hasSource("android")
                hasType("view")
                hasViewName(viewName)
                hasResourceCount(0)
                hasErrorCount(1)
            }
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
