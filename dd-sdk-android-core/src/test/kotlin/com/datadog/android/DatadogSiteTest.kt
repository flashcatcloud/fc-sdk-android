/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package com.datadog.android

import com.datadog.android.utils.forge.Configurator
import com.flashcat.android.DatadogSite
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogSiteTest {

    @Test
    fun `M return intake endpoint W intakeEndpoint {CN}`() {
        assertThat(DatadogSite.CN.intakeEndpoint).isEqualTo("https://browser.flashcat.cloud")
    }

    @Test
    fun `M return intake endpoint W intakeEndpoint {STAGING}`() {
        assertThat(DatadogSite.STAGING.intakeEndpoint).isEqualTo("https://jira.flashcat.cloud")
    }
}
