/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package com.datadog.android.flags.internal

import com.flashcat.android.DatadogSite

/**
 * Gets the complete flags endpoint URL.
 * @param customerDomain The customer-specific subdomain prefix for the flags CDN.
 * This is used to construct the full host in the format: `<customerDomain>.ff-cdn.<site>.<tld>`
 * @return Complete flags endpoint URL or null if site not supported
 */
internal fun DatadogSite.getFlagsEndpoint(customerDomain: String): String? {
    val host = flagsHost(customerDomain) ?: return null
    return "https://$host$FLAGS_PATH"
}

/**
 * Extension function that returns the flags CDN host for this Datadog site.
 * @param customerDomain The customer-specific subdomain prefix for the flags CDN
 * @return Flags host string in format `<customerDomain>.ff-cdn.<site>.<tld>`, or null if site not supported
 */
private fun DatadogSite.flagsHost(customerDomain: String): String? = when (this) {
    DatadogSite.STAGING -> "$customerDomain.ff-cdn.datad0g.com"
    DatadogSite.CN -> buildFlagsHostString(customerDomain) // No site in the host, default .com TLD
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
