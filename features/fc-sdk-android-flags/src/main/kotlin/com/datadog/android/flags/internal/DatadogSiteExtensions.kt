/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.android.DatadogSite

/**
 * Gets the complete flags endpoint URL.
 * Note: Feature Flags functionality is not yet supported for FlashCat.
 * @param customerDomain The customer-specific subdomain prefix for the flags CDN.
 * @return Complete flags endpoint URL or null (currently returns null for all FlashCat sites)
 */
internal fun DatadogSite.getFlagsEndpoint(customerDomain: String): String? {
    // Feature Flags not yet supported for FlashCat
    return null
}

private const val FLAGS_PATH = "/precompute-assignments"
