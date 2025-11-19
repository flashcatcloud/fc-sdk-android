/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.internal.profiler

import com.datadog.tools.annotation.NoOpImplementation

/**
 * Interface of benchmark span to be implemented. This should only used by internal benchmarking.
 */
@NoOpImplementation
interface BenchmarkSpan {

    /**
     * Marks the end of [BenchmarkSpan] execution.
     */
    fun stop()
}
