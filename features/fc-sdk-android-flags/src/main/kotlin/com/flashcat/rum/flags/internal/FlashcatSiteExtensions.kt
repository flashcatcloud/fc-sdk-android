/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.flags.internal

import com.flashcat.rum.FlashcatSite

/**
 * Gets the complete flags endpoint URL.
 * @param customerDomain The customer-specific subdomain prefix for the flags CDN.
 * This is used to construct the full host in the format: `<customerDomain>.ff-cdn.<site>.<tld>`
 * @return Complete flags endpoint URL or null if site not supported
 */
internal fun FlashcatSite.getFlagsEndpoint(customerDomain: String): String? {
    val host = flagsHost(customerDomain) ?: return null
    return "https://$host$FLAGS_PATH"
}

/**
 * Extension function that returns the flags CDN host for this Datadog site.
 * @param customerDomain The customer-specific subdomain prefix for the flags CDN
 * @return Flags host string in format `<customerDomain>.ff-cdn.<site>.<tld>`, or null if site not supported
 */
private fun FlashcatSite.flagsHost(customerDomain: String): String? = when (this) {
    FlashcatSite.US1_FED -> null

    FlashcatSite.STAGING -> "$customerDomain.ff-cdn.datad0g.com"
    FlashcatSite.EU1 -> buildFlagsHostString(customerDomain, tld = "eu") // No site in the host, .eu TLD
    FlashcatSite.US1 -> buildFlagsHostString(customerDomain) // No site in the host, default .com TLD

    FlashcatSite.US3 -> buildFlagsHostString(customerDomain, dc = "us3", tld = "com")
    FlashcatSite.US5 -> buildFlagsHostString(customerDomain, dc = "us5", tld = "com")
    FlashcatSite.AP1 -> buildFlagsHostString(customerDomain, dc = "ap1", tld = "com")
    FlashcatSite.AP2 -> buildFlagsHostString(customerDomain, dc = "ap2", tld = "com")
}

private fun buildFlagsHostString(customerDomain: String, dc: String? = null, tld: String = "com"): String {
    val parts = listOfNotNull(
        customerDomain,
        "ff-cdn",
        dc,
        "datadoghq",
        tld
    )
    return parts.joinToString(".")
}

private const val FLAGS_PATH = "/precompute-assignments"
