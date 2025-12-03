/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.flags.internal.model

import cloud.flashcat.android.FlashcatSite
import cloud.flashcat.android.api.context.DatadogContext
import cloud.flashcat.android.flags.FlagsConfiguration
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(ForgeExtension::class),
    ExtendWith(MockitoExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class FlagsContextTest {

    @Mock
    lateinit var mockDatadogContext: DatadogContext

    @Mock
    lateinit var mockFlashcatSite: FlashcatSite

    @StringForgery
    lateinit var fakeApplicationId: String

    @StringForgery
    lateinit var fakeClientToken: String

    @StringForgery
    lateinit var fakeSiteName: String

    @StringForgery
    lateinit var fakeEnv: String

    @Test
    fun `M create FlagsContext with all parameters W create() { complete configuration }`(
        @StringForgery(regex = "https://[a-z]+\\.com(/[a-z]+)+") fakeExposureEndpoint: String,
        @StringForgery(regex = "https://[a-z]+\\.com(/[a-z]+)+") fakeFlagEndpoint: String
    ) {
        // Given
        whenever(mockDatadogContext.clientToken) doReturn fakeClientToken
        whenever(mockDatadogContext.site) doReturn mockFlashcatSite
        whenever(mockFlashcatSite.name) doReturn fakeSiteName
        whenever(mockDatadogContext.env) doReturn fakeEnv

        val flagsConfiguration = FlagsConfiguration.Builder()
            .useCustomExposureEndpoint(fakeExposureEndpoint)
            .useCustomFlagEndpoint(fakeFlagEndpoint)
            .build()

        // When
        val flagsContext = FlagsContext.create(mockDatadogContext, fakeApplicationId, flagsConfiguration)

        // Then
        assertThat(flagsContext.applicationId).isEqualTo(fakeApplicationId)
        assertThat(flagsContext.clientToken).isEqualTo(fakeClientToken)
        assertThat(flagsContext.site).isEqualTo(mockFlashcatSite)
        assertThat(flagsContext.env).isEqualTo(fakeEnv)
        assertThat(flagsContext.customExposureEndpoint).isEqualTo(fakeExposureEndpoint)
        assertThat(flagsContext.customFlagEndpoint).isEqualTo(fakeFlagEndpoint)
    }

    @Test
    fun `M create FlagsContext with defaults W create() { minimal configuration }`() {
        // Given
        whenever(mockDatadogContext.clientToken) doReturn fakeClientToken
        whenever(mockDatadogContext.site) doReturn mockFlashcatSite
        whenever(mockFlashcatSite.name) doReturn fakeSiteName
        whenever(mockDatadogContext.env) doReturn fakeEnv

        val flagsConfiguration = FlagsConfiguration.Builder().build()

        // When
        val flagsContext = FlagsContext.create(mockDatadogContext, fakeApplicationId, flagsConfiguration)

        // Then
        assertThat(flagsContext.applicationId).isEqualTo(fakeApplicationId)
        assertThat(flagsContext.clientToken).isEqualTo(fakeClientToken)
        assertThat(flagsContext.site).isEqualTo(mockFlashcatSite)
        assertThat(flagsContext.env).isEqualTo(fakeEnv)
        assertThat(flagsContext.customExposureEndpoint).isNull()
        assertThat(flagsContext.customFlagEndpoint).isNull()
    }

    @Test
    fun `M handle null application ID W create() { null app ID }`() {
        // Given
        whenever(mockDatadogContext.clientToken) doReturn fakeClientToken
        whenever(mockDatadogContext.site) doReturn mockFlashcatSite
        whenever(mockDatadogContext.site.name) doReturn fakeSiteName
        whenever(mockDatadogContext.env) doReturn fakeEnv

        val flagsConfiguration = FlagsConfiguration.Builder().build()

        // When
        val flagsContext = FlagsContext.create(mockDatadogContext, null, flagsConfiguration)

        // Then
        assertThat(flagsContext.applicationId).isNull()
        assertThat(flagsContext.clientToken).isEqualTo(fakeClientToken)
        assertThat(flagsContext.site).isEqualTo(mockFlashcatSite)
        assertThat(flagsContext.env).isEqualTo(fakeEnv)
    }
}
