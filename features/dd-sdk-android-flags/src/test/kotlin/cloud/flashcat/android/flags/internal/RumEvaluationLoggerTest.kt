/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.flags.internal

import cloud.flashcat.android.api.feature.FeatureScope
import cloud.flashcat.android.flags.utils.forge.ForgeConfigurator
import cloud.flashcat.android.internal.flags.RumFlagEvaluationMessage
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
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class RumEvaluationLoggerTest {

    @Mock
    lateinit var mockFeatureScope: FeatureScope

    private lateinit var testedLogger: DefaultRumEvaluationLogger

    @BeforeEach
    fun `set up`() {
        testedLogger = DefaultRumEvaluationLogger(
            featureScope = mockFeatureScope
        )
    }

    // region logEvaluation

    @Test
    fun `M send RumFlagEvaluationMessage W logEvaluation()`(forge: Forge) {
        // Given
        val fakeFlagKey = forge.anAlphabeticalString()
        val fakeValue = forge.anAsciiString()

        // When
        testedLogger.logEvaluation(
            flagKey = fakeFlagKey,
            value = fakeValue
        )

        // Then
        argumentCaptor<RumFlagEvaluationMessage> {
            verify(mockFeatureScope).sendEvent(capture())
            assertThat(lastValue.flagKey).isEqualTo(fakeFlagKey)
            assertThat(lastValue.value).isEqualTo(fakeValue)
        }
    }

    // endregion
}
