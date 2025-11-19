/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sample.webview

import com.flashcat.rum.FlashcatSite
import com.flashcat.rum.sample.BuildConfig
import timber.log.Timber

internal val BROWSER_SITE: String
    get() {
        return try {
            FlashcatSite.valueOf(BuildConfig.DD_SITE_NAME)
        } catch (e: IllegalArgumentException) {
            Timber.e("Error setting site to ${BuildConfig.DD_SITE_NAME}")
            null
        }.browserSite()
    }

private fun FlashcatSite?.browserSite(): String {
    return when (this) {
        FlashcatSite.US1,
        FlashcatSite.STAGING,
        null -> "flashcat.cloud"

        FlashcatSite.US3 -> "us3.flashcat.cloud"
        FlashcatSite.US5 -> "us5.flashcat.cloud"
        FlashcatSite.EU1 -> "datadoghq.eu"
        FlashcatSite.AP1 -> "ap1.flashcat.cloud"
        FlashcatSite.AP2 -> "ap2.flashcat.cloud"
        FlashcatSite.US1_FED -> "ddog-gov.com"
    }
}
