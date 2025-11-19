/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.rum.internal.metric

import com.flashcat.rum.api.InternalLogger
import com.flashcat.rum.core.metrics.PerformanceMetric
import com.flashcat.rum.core.metrics.TelemetryMetricType
import com.flashcat.rum.internal.telemetry.InternalTelemetryEvent

class FakeInternalLogger : InternalLogger {

    var lastMetric: Pair<String, Map<String, Any?>>? = null

    var errorLog: String? = null

    override fun log(
        level: InternalLogger.Level,
        target: InternalLogger.Target,
        messageBuilder: () -> String,
        throwable: Throwable?,
        onlyOnce: Boolean,
        additionalProperties: Map<String, Any?>?
    ) {
        errorLog = messageBuilder()
    }

    override fun log(
        level: InternalLogger.Level,
        targets: List<InternalLogger.Target>,
        messageBuilder: () -> String,
        throwable: Throwable?,
        onlyOnce: Boolean,
        additionalProperties: Map<String, Any?>?
    ) {
        // do nothing
    }

    override fun logMetric(
        messageBuilder: () -> String,
        additionalProperties: Map<String, Any?>,
        samplingRate: Float,
        creationSampleRate: Float?
    ) {
        lastMetric = Pair(messageBuilder(), additionalProperties)
    }

    override fun startPerformanceMeasure(
        callerClass: String,
        metric: TelemetryMetricType,
        samplingRate: Float,
        operationName: String
    ): PerformanceMetric? {
        // do nothing
        return null
    }

    override fun logApiUsage(
        samplingRate: Float,
        apiUsageEventBuilder: () -> InternalTelemetryEvent.ApiUsage
    ) {
        // do nothing
    }
}
