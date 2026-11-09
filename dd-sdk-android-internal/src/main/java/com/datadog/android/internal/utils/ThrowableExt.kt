/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.utils

import java.io.PrintWriter
import java.io.StringWriter

/**
 * Converts stacktrace to string format.
 *
 * The result is bounded (see [limitStackTraceLength]): ART does not cap the depth of a
 * [StackOverflowError], and an unbounded trace makes the whole event exceed the persistence
 * size limit, so the error would be dropped instead of reported.
 */
fun Throwable.loggableStackTrace(): String {
    val stringWriter = StringWriter()
    @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
    printStackTrace(PrintWriter(stringWriter))
    return stringWriter.toString().limitStackTraceLength()
}

/**
 * Keeps a stack trace within [MAX_STACK_TRACE_LENGTH] characters (plus a one-line marker).
 *
 * The head holds the frames where the error was raised; the tail holds the entry point of a
 * deep recursion and the trailing "Caused by" sections. Only the middle is dropped, cut on line
 * boundaries when possible.
 */
internal fun String.limitStackTraceLength(): String {
    if (length <= MAX_STACK_TRACE_LENGTH) return this

    val headLimit = MAX_STACK_TRACE_LENGTH - STACK_TRACE_TAIL_LENGTH
    val headEnd = lastIndexOf('\n', headLimit).takeIf { it > 0 } ?: headLimit
    val tailLimit = length - STACK_TRACE_TAIL_LENGTH
    val tailStart = indexOf('\n', tailLimit).takeIf { it >= 0 }?.plus(1) ?: tailLimit
    // Each dropped line ends with '\n'; the extra one is the newline ending the head.
    val omittedLines = (substring(headEnd, tailStart).count { it == '\n' } - 1).coerceAtLeast(0)

    return substring(0, headEnd) + "\n\t... $omittedLines lines truncated\n" + substring(tailStart)
}

internal const val MAX_STACK_TRACE_LENGTH: Int = 64 * 1024
internal const val STACK_TRACE_TAIL_LENGTH: Int = 16 * 1024
