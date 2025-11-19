/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sdk.utils

import android.content.Intent
import com.flashcat.rum.privacy.TrackingConsent

fun Intent.addExtras(map: Map<String, Any?>) {
    map.forEach {
        val value = it.value
        val key = it.key
        when (value) {
            is String -> putExtra(key, value)
            is Number -> putExtra(key, value)
            is Boolean -> putExtra(key, value)
            is Enum<*> -> putExtra(key, value)
        }
    }
}

fun Intent.addTrackingConsent(consent: TrackingConsent) {
    this.putExtra(TRACKING_CONSENT_KEY, consent)
}

fun Intent.addForgeSeed(seed: Long) = this.putExtra(FORGE_SEED_KEY, seed)
