/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.compose.internal

import com.flashcat.rum.Flashcat
import com.flashcat.rum.api.InternalLogger
import com.flashcat.rum.api.SdkCore
import com.flashcat.rum.api.feature.FeatureSdkCore

internal fun sendTelemetry(
    autoInstrumented: Boolean = false,
    instrumentationType: InstrumentationType,
    supportLibrary: SupportLibrary? = null,
    sdkCore: SdkCore = Datadog.getInstance()
) {
    val message = "$DATADOG_SEMANTICS_TELEMETRY_LOG: ${instrumentationType.value}"
    val attributes = mapOf(
        KEY_COMPOSE_INSTRUMENTATION to mapOf(
            KEY_ENABLED to autoInstrumented,
            KEY_INSTRUMENTATION_TYPE to instrumentationType,
            KEY_SUPPORT_LIBRARY to supportLibrary
        )
    )
    (sdkCore as? FeatureSdkCore)?.internalLogger?.log(
        level = InternalLogger.Level.INFO,
        target = InternalLogger.Target.TELEMETRY,
        messageBuilder = { message },
        onlyOnce = true,
        additionalProperties = attributes
    )
}

internal enum class InstrumentationType(val value: String) {
    Semantics("Semantics"),
    ViewTracking("ViewTracking")
}

internal enum class SupportLibrary(val value: String) {
    Navigation("Navigation"),
    Navigation3("Navigation3")
}

private const val KEY_COMPOSE_INSTRUMENTATION = "compose_instrumentation"

private const val KEY_ENABLED = "enabled"

private const val KEY_SUPPORT_LIBRARY = "support_library"

private const val KEY_INSTRUMENTATION_TYPE = "instrumentation_type"

private const val DATADOG_SEMANTICS_TELEMETRY_LOG =
    "Flashcat Compose Integration Telemetry"
