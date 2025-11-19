/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.flags.internal

import com.flashcat.rum.FlashcatSite
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

@ExtendWith(ForgeExtension::class)
internal class FlashcatSiteExtensionsTest {

    // region getFlagsEndpoint - With Custom Domain

    @ParameterizedTest
    @MethodSource("supportedSitesWithCustomDomain")
    fun `M build flags endpoint W getFlagsEndpoint() { supported sites with custom domain }`(
        site: FlashcatSite,
        expectedHostSuffix: String,
        @StringForgery customerDomain: String
    ) {
        // When
        val result = site.getFlagsEndpoint(customerDomain)

        // Then
        assertThat(result).isEqualTo("https://$customerDomain.$expectedHostSuffix/precompute-assignments")
    }

    // endregion

    // region getFlagsEndpoint - With Default Domain

    @ParameterizedTest
    @MethodSource("supportedSitesWithDefaultDomain")
    fun `M build flags endpoint W getFlagsEndpoint() { supported sites with preview domain }`(
        site: FlashcatSite,
        expectedHost: String
    ) {
        // When
        val result = site.getFlagsEndpoint("preview")

        // Then
        assertThat(result).isEqualTo("https://$expectedHost/precompute-assignments")
    }

    // endregion

    // region getFlagsEndpoint - Error Cases

    @Test
    fun `M return null W getFlagsEndpoint() { unsupported site }`(@StringForgery customerDomain: String) {
        // When
        val result = FlashcatSite.US1_FED.getFlagsEndpoint(customerDomain)

        // Then
        assertThat(result).isNull()
    }

    // endregion

    // region getFlagsEndpoint - Edge Cases

    @ParameterizedTest
    @MethodSource("edgeCaseCustomerDomains")
    fun `M handle edge case customer domains W getFlagsEndpoint() { various edge cases }`(
        site: FlashcatSite,
        customerDomain: String,
        expectedHost: String
    ) {
        // When
        val result = site.getFlagsEndpoint(customerDomain)

        // Then
        assertThat(result).isEqualTo("https://$expectedHost/precompute-assignments")
    }

    // endregion

    companion object {
        @Suppress("unused")
        @JvmStatic
        fun supportedSitesWithCustomDomain(): List<Arguments> = listOf(
            Arguments.of(FlashcatSite.US1, "ff-cdn.flashcat.cloud"),
            Arguments.of(FlashcatSite.US3, "ff-cdn.us3.flashcat.cloud"),
            Arguments.of(FlashcatSite.US5, "ff-cdn.us5.flashcat.cloud"),
            Arguments.of(FlashcatSite.AP1, "ff-cdn.ap1.flashcat.cloud"),
            Arguments.of(FlashcatSite.AP2, "ff-cdn.ap2.flashcat.cloud"),
            Arguments.of(FlashcatSite.EU1, "ff-cdn.datadoghq.eu"),
            Arguments.of(FlashcatSite.STAGING, "ff-cdn.datad0g.com")
        )

        @Suppress("unused")
        @JvmStatic
        fun supportedSitesWithDefaultDomain(): List<Arguments> = listOf(
            Arguments.of(FlashcatSite.US1, "preview.ff-cdn.flashcat.cloud"),
            Arguments.of(FlashcatSite.EU1, "preview.ff-cdn.datadoghq.eu"),
            Arguments.of(FlashcatSite.STAGING, "preview.ff-cdn.datad0g.com")
        )

        @Suppress("unused")
        @JvmStatic
        fun edgeCaseCustomerDomains(): List<Arguments> = listOf(
            // Domain with hyphens and underscores (special characters)
            Arguments.of(FlashcatSite.US1, "test-domain_123", "test-domain_123.ff-cdn.flashcat.cloud"),
            // Numeric-only domain
            Arguments.of(FlashcatSite.US3, "12345", "12345.ff-cdn.us3.flashcat.cloud"),
            // Domain with dots (subdomain-like)
            Arguments.of(FlashcatSite.EU1, "my.customer.domain", "my.customer.domain.ff-cdn.datadoghq.eu")
        )
    }
}
