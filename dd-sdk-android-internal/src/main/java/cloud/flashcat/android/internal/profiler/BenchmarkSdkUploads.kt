/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.internal.profiler

import cloud.flashcat.tools.annotation.NoOpImplementation

/**
 * Interface of benchmark SDK performance to be implemented. This should only be used by internal
 * benchmarking.
 */
@NoOpImplementation
interface BenchmarkSdkUploads {

    /**
     * Get a [BenchmarkMeter] for the given operation.
     * @param operation The operation name.
     * @return The [BenchmarkMeter] for the given operation.
     */
    fun getMeter(operation: String): BenchmarkMeter
}
