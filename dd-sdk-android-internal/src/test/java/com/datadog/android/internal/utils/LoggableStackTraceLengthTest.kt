/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class LoggableStackTraceLengthTest {

    @Test
    fun `M keep stack trace unchanged W loggableStackTrace() {within limit}`() {
        // Given
        val throwable = IllegalStateException("boom")

        // When
        val result = throwable.loggableStackTrace()

        // Then
        assertThat(result).isEqualTo(throwable.stackTraceToString())
    }

    @Test
    fun `M bound stack trace W loggableStackTrace() {deep recursion}`() {
        // Given
        // ART does not cap the depth of a StackOverflowError the way the JVM does
        val throwable = StackOverflowError("stack size 8MB")
        val entry = StackTraceElement("com.example.Entry", "start", "Entry.kt", 42)
        throwable.stackTrace = recursionFrames() + entry
        val full = throwable.stackTraceToString()

        // When
        val result = throwable.loggableStackTrace()

        // Then
        assertThat(full.length).isGreaterThan(MAX_STACK_TRACE_LENGTH)
        assertThat(result.length).isLessThanOrEqualTo(MAX_STACK_TRACE_LENGTH + MARKER_ALLOWANCE)
        val lines = result.lines()
        assertThat(lines[0]).isEqualTo("java.lang.StackOverflowError: stack size 8MB")
        assertThat(lines[1]).contains("com.example.Recursion.recurse(Recursion.kt:0)")
        assertThat(result).contains("com.example.Entry.start(Entry.kt:42)")
        val marker = lines.single { it.contains("lines truncated") }
        val omitted = marker.trim().removePrefix("... ").removeSuffix(" lines truncated").toInt()
        // Only the marker replaces the dropped lines: nothing else is lost or duplicated
        assertThat(lines.size - 1 + omitted).isEqualTo(full.lines().size)
        assertThat(full).startsWith(result.substringBefore("\n\t... "))
        assertThat(full).endsWith(result.substringAfter(" lines truncated\n"))
    }

    @Test
    fun `M keep trailing cause W loggableStackTrace() {deep recursion with cause}`() {
        // Given
        val throwable = StackOverflowError()
        throwable.initCause(IllegalStateException("root cause"))
        throwable.stackTrace = recursionFrames()

        // When
        val result = throwable.loggableStackTrace()

        // Then
        assertThat(result.length).isLessThanOrEqualTo(MAX_STACK_TRACE_LENGTH + MARKER_ALLOWANCE)
        assertThat(result).contains("Caused by: java.lang.IllegalStateException: root cause")
    }

    @Test
    fun `M bound stack trace W loggableStackTrace() {deep thread stack}`() {
        // Given
        val stack = recursionFrames()

        // When
        val result = stack.loggableStackTrace()

        // Then
        assertThat(result.length).isLessThanOrEqualTo(MAX_STACK_TRACE_LENGTH + MARKER_ALLOWANCE)
        assertThat(result.lines().first()).isEqualTo("at ${stack.first()}")
        assertThat(result.lines().last()).isEqualTo("at ${stack.last()}")
    }

    @Test
    fun `M cut by characters W limitStackTraceLength() {single huge line}`() {
        // Given
        val huge = "x".repeat(MAX_STACK_TRACE_LENGTH * 2)

        // When
        val result = huge.limitStackTraceLength()

        // Then
        assertThat(result.length).isLessThanOrEqualTo(MAX_STACK_TRACE_LENGTH + MARKER_ALLOWANCE)
        assertThat(result).contains("\t... 0 lines truncated\n")
    }

    private fun recursionFrames() = Array(DEEP_RECURSION_FRAMES) { i ->
        StackTraceElement("com.example.Recursion", "recurse", "Recursion.kt", i)
    }

    companion object {
        private const val DEEP_RECURSION_FRAMES = 20_000
        private const val MARKER_ALLOWANCE = 64
    }
}
