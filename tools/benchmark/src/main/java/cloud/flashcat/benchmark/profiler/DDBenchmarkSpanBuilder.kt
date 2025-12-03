/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.benchmark.profiler

import cloud.flashcat.android.internal.profiler.BenchmarkSpan
import cloud.flashcat.android.internal.profiler.BenchmarkSpanBuilder
import io.opentelemetry.api.trace.SpanBuilder

/**
 * Implementation of [BenchmarkSpanBuilder].
 */
class DDBenchmarkSpanBuilder(
    private val spanBuilder: SpanBuilder
) : BenchmarkSpanBuilder {

    override fun startSpan(): BenchmarkSpan {
        val span = spanBuilder.startSpan()
        return DDBenchmarkSpan(span, span.makeCurrent())
    }
}
