/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.utils.extension

import cloud.flashcat.android.log.internal.domain.DatadogLogGenerator
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

fun Long.toIsoFormattedTimestamp(iso: String = DatadogLogGenerator.ISO_8601): String {
    val simpleDateFormat = SimpleDateFormat(iso, Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return simpleDateFormat.format(this)
}
