/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.okhttp.utils

import com.flashcat.rum.rum.GlobalRumMonitor
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.isAccessible

internal fun GlobalRumMonitor.reset() {
    this::class.memberFunctions
        .first { it.name == "reset" }
        .apply { this.isAccessible = true }
        .call(this::class.objectInstance)
}
