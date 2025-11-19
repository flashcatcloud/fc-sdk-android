/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.flashcat.rum.trace.internal

import com.flashcat.rum.api.feature.FeatureSdkCore
import com.flashcat.rum.trace.api.scope.DatadogScopeListener
import com.flashcat.rum.trace.api.tracer.DatadogTracer

internal class TracePropagationScopeListener(
    private val sdkCore: FeatureSdkCore,
    private val datadogTracer: DatadogTracer
) : DatadogScopeListener {
    override fun afterScopeActivated() {
        val activeSpanContext = datadogTracer.activeSpan()?.context()
        if (activeSpanContext != null) {
            val activeSpanId = activeSpanContext.spanId.toString()
            val activeTraceId = activeSpanContext.traceId.toHexString()
            sdkCore.addActiveTraceToContext(activeTraceId, activeSpanId)
        }
    }

    override fun afterScopeClosed() {
        sdkCore.removeActiveTraceFromContext()
    }
}
