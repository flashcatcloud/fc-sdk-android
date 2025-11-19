/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.trace.api

import com.flashcat.rum.api.context.FlashcatContext
import com.flashcat.rum.trace.GlobalDatadogTracer
import com.flashcat.rum.trace.api.span.DatadogSpan
import com.flashcat.rum.trace.api.span.DatadogSpanContext
import com.flashcat.rum.trace.api.trace.DatadogTraceId
import com.flashcat.rum.trace.api.tracer.DatadogTracer
import com.flashcat.rum.trace.api.tracer.DatadogTracerBuilder
import com.flashcat.rum.trace.internal.DatadogPropagationHelper
import com.flashcat.rum.trace.internal.DatadogSpanAdapter
import com.flashcat.rum.trace.internal.DatadogSpanContextAdapter
import com.flashcat.rum.trace.internal.DatadogTraceIdAdapter
import com.flashcat.rum.trace.internal.DatadogTracerAdapter
import com.flashcat.rum.trace.internal.DatadogTracerBuilderAdapter
import com.flashcat.rum.trace.internal.DatadogTracingToolkit
import com.flashcat.rum.trace.internal.domain.event.CoreTracerSpanToSpanEventMapper
import com.datadog.trace.api.DDTraceId
import com.datadog.trace.core.CoreTracer
import com.datadog.trace.core.DDSpan
import com.datadog.trace.core.DDSpanContext
import com.google.gson.JsonElement

val DatadogTracer.partialFlushMinSpans: Int?
    get() = coreTracer?.partialFlushMinSpans

val DatadogSpanContext.resourceName: String?
    get() = ddSpanContext?.resourceName?.toString()

val DatadogSpanContext.serviceName: String?
    get() = ddSpanContext?.serviceName?.toString()

val DatadogTraceId.Companion.ZERO: DatadogTraceId
    get() = DatadogTraceIdAdapter(DDTraceId.ZERO)

fun DatadogTraceId.Companion.from(traceId: Long): DatadogTraceId {
    return DatadogTraceIdAdapter(DDTraceId.from(traceId))
}

fun DatadogTraceId.Companion.from(traceId: String): DatadogTraceId {
    return DatadogTraceIdAdapter(DDTraceId.from(traceId))
}

fun DatadogSpan.resolveMeta(flashcatContext: FlashcatContext): JsonElement {
    val mapper = CoreTracerSpanToSpanEventMapper(false)
    val ddSpan = (this as DatadogSpanAdapter).delegate as DDSpan
    return mapper.resolveMeta(flashcatContext, ddSpan).toJson()
}

fun DatadogSpan.resolveMetrics(): JsonElement {
    val mapper = CoreTracerSpanToSpanEventMapper(false)
    val ddSpan = (this as DatadogSpanAdapter).delegate as DDSpan
    return mapper.resolveMetrics(ddSpan).toJson()
}

fun DatadogSpan.forceSamplingDecision() {
    (this as DatadogSpanAdapter).delegate.forceSamplingDecision()
}

fun DatadogTracingToolkit.setTracingAdapterBuilderMock(mock: DatadogTracerBuilder?) {
    testBuilderProvider = mock
}

fun DatadogTracingToolkit.clear() {
    setTracingAdapterBuilderMock(null)
}

fun DatadogTracingToolkit.withMockPropagationHelper(
    mockHelper: DatadogPropagationHelper,
    block: DatadogTracingToolkit.() -> Unit
) {
    val helper = propagationHelper
    try {
        propagationHelper = mockHelper
        block()
    } finally {
        propagationHelper = helper
    }
}

fun DatadogTracerBuilder.setTestIdGenerationStrategy(strategy: TestIdGenerationStrategy) = apply {
    (this as? DatadogTracerBuilderAdapter)?.setCustomIdGenerationStrategy(strategy)
}

fun GlobalDatadogTracer.replace(
    builder: DatadogTracerBuilder
): Boolean {
    clear()
    return registerIfAbsent(builder.build())
}

private val DatadogSpanContext.ddSpanContext: DDSpanContext?
    get() {
        val spanContextAdapter = this as? DatadogSpanContextAdapter
        return spanContextAdapter?.delegate as? DDSpanContext
    }

private val DatadogTracer.coreTracer: CoreTracer?
    get() {
        val tracerAdapter = this as? DatadogTracerAdapter
        return tracerAdapter?.delegate as? CoreTracer
    }
