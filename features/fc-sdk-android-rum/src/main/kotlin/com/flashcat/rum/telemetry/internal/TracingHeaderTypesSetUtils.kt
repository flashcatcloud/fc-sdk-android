/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.telemetry.internal

import com.flashcat.rum.internal.telemetry.TracingHeaderType
import com.flashcat.rum.internal.telemetry.TracingHeaderTypesSet
import com.flashcat.rum.telemetry.model.TelemetryConfigurationEvent

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
