/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.internal.utils

import java.io.PrintWriter
import java.io.StringWriter

/**
 * Converts stacktrace to string format.
 */
fun Throwable.loggableStackTrace(): String {
    val stringWriter = StringWriter()
    @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
    printStackTrace(PrintWriter(stringWriter))
    return stringWriter.toString()
}
