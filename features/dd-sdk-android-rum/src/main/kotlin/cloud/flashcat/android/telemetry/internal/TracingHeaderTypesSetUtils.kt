/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.telemetry.internal

import cloud.flashcat.android.internal.telemetry.TracingHeaderType
import cloud.flashcat.android.internal.telemetry.TracingHeaderTypesSet
import cloud.flashcat.android.telemetry.model.TelemetryConfigurationEvent

internal fun TracingHeaderTypesSet.toSelectedTracingPropagators() = types.map { it.toSelectedTracingPropagator() }

private fun TracingHeaderType.toSelectedTracingPropagator(): TelemetryConfigurationEvent.SelectedTracingPropagator {
    return when (this) {
        TracingHeaderType.DATADOG ->
            TelemetryConfigurationEvent.SelectedTracingPropagator.DATADOG

        TracingHeaderType.B3 ->
            TelemetryConfigurationEvent.SelectedTracingPropagator.B3

        TracingHeaderType.B3MULTI ->
            TelemetryConfigurationEvent.SelectedTracingPropagator.B3MULTI

        TracingHeaderType.TRACECONTEXT ->
            TelemetryConfigurationEvent.SelectedTracingPropagator.TRACECONTEXT
    }
}
