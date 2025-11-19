/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.utils.extension

import android.util.Log
import com.flashcat.rum.log.internal.domain.DatadogLogGenerator
import com.flashcat.rum.log.model.LogEvent

fun Int.asLogStatus(): LogEvent.Status {
    return when (this) {
        Log.ASSERT -> LogEvent.Status.CRITICAL
        Log.ERROR -> LogEvent.Status.ERROR
        Log.WARN -> LogEvent.Status.WARN
        Log.INFO -> LogEvent.Status.INFO
        Log.DEBUG -> LogEvent.Status.DEBUG
        Log.VERBOSE -> LogEvent.Status.TRACE
        DatadogLogGenerator.CRASH -> LogEvent.Status.EMERGENCY
        else -> LogEvent.Status.DEBUG
    }
}
