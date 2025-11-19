/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.vendor.sample

internal data class SseEvent(val data: String) {

    fun toJson(): String {
        return "{\"data\":\"$data\"}"
    }
}
