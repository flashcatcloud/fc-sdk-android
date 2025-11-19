/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sessionreplay.internal.recorder

import com.flashcat.rum.internal.profiler.BenchmarkSpan
import com.flashcat.rum.internal.profiler.withinBenchmarkSpan

private const val ATTRIBUTE_CONTAINER = "attribute.container"

/**
 * A wrap function of [withinSRBenchmarkSpan] dedicated to session replay span recording.
 */
internal inline fun <T : Any?> withinSRBenchmarkSpan(
    spanName: String,
    isContainer: Boolean = false,
    block: BenchmarkSpan.() -> T
): T {
    return withinBenchmarkSpan(
        spanName,
        mapOf(ATTRIBUTE_CONTAINER to isContainer.toString()),
        block
    )
}
