/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.internal.profiler

import com.datadog.tools.annotation.NoOpImplementation

/**
 * Interface of benchmark span builder. This should only used by internal benchmarking.
 */
@NoOpImplementation
interface BenchmarkSpanBuilder {

    /**
     * Returns a new [BenchmarkSpan] and start the span.
     */
    fun startSpan(): BenchmarkSpan
}
