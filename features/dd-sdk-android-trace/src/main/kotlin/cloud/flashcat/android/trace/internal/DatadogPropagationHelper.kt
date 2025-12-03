/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */
package cloud.flashcat.android.trace.internal

import cloud.flashcat.android.lint.InternalApi
import cloud.flashcat.android.trace.api.span.DatadogSpanContext
import cloud.flashcat.trace.api.DDSpanId
import cloud.flashcat.trace.api.DDTraceId
import cloud.flashcat.trace.core.propagation.ExtractedContext

/**
 * For internal usage only.
 * Helper class for handling Datadog context propagation.
 */
@InternalApi
class DatadogPropagationHelper internal constructor() {
    /**
     * Determines if the provided [DatadogSpanContext] represents an extracted context.
     *
     * @param context The [DatadogSpanContext] to be evaluated.
     * @return True if the context is identified as an extracted context, otherwise false.
     */
    fun isExtractedContext(context: DatadogSpanContext): Boolean {
        if (context !is DatadogSpanContextAdapter) return false
        return context.delegate is ExtractedContext
    }

    /**
     * Creates a [DatadogSpanContext] object that represents an extracted context from the given parameters.
     *
     * @param traceId The unique identifier for the trace.
     * @param spanId The unique identifier for the span.
     * @param samplingPriority The sampling priority value for determining the trace's sampling behavior.
     * @return A [DatadogSpanContext] instance containing the extracted context.
     */
    fun createExtractedContext(
        traceId: String,
        spanId: String,
        samplingPriority: Int
    ): DatadogSpanContext = DatadogSpanContextAdapter(
        ExtractedContext(
            DDTraceId.fromHexOrDefault(traceId, DDTraceId.ZERO),
            DDSpanId.fromHexOrDefault(spanId, DDSpanId.ZERO),
            samplingPriority,
            null,
            null,
            null
        )
    )
}
