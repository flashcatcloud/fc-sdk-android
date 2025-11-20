/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.android.DatadogSite
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
internal class DatadogSiteExtensionsTest {

    @Test
    fun `M return null W getFlagsEndpoint() { CN site - feature not supported }`(
        @StringForgery customerDomain: String
    ) {
        // When
        val result = DatadogSite.CN.getFlagsEndpoint(customerDomain)

        // Then - Feature Flags not yet supported for FlashCat
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W getFlagsEndpoint() { STAGING site - feature not supported }`(
        @StringForgery customerDomain: String
    ) {
        // When
        val result = DatadogSite.STAGING.getFlagsEndpoint(customerDomain)

        // Then - Feature Flags not yet supported for FlashCat
        assertThat(result).isNull()
    }
}
