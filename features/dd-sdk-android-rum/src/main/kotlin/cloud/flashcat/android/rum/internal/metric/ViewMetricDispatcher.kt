/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */
package cloud.flashcat.android.rum.internal.metric

import cloud.flashcat.tools.annotation.NoOpImplementation

@NoOpImplementation
internal interface ViewMetricDispatcher {
    fun sendViewEnded(
        invState: ViewInitializationMetricsState,
        tnsState: ViewInitializationMetricsState
    )

    fun onDurationResolved(newDuration: Long)
    fun onViewLoadingTimeResolved(newLoadingTime: Long)
}
