/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.benchmark.profiler

import cloud.flashcat.android.internal.profiler.BenchmarkProfiler
import cloud.flashcat.android.internal.profiler.BenchmarkTracer
import io.opentelemetry.api.GlobalOpenTelemetry

/**
 * Implementation of [BenchmarkProfiler].
 */
class DDBenchmarkProfiler : BenchmarkProfiler {

    override fun getTracer(operation: String): BenchmarkTracer {
        return DDBenchmarkTracer(GlobalOpenTelemetry.get().getTracer(operation))
    }
}
