/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.trace.api

import cloud.flashcat.android.api.context.DatadogContext
import cloud.flashcat.android.trace.GlobalDatadogTracer
import cloud.flashcat.android.trace.api.span.DatadogSpan
import cloud.flashcat.android.trace.api.span.DatadogSpanContext
import cloud.flashcat.android.trace.api.trace.DatadogTraceId
import cloud.flashcat.android.trace.api.tracer.DatadogTracer
import cloud.flashcat.android.trace.api.tracer.DatadogTracerBuilder
import cloud.flashcat.android.trace.internal.DatadogPropagationHelper
import cloud.flashcat.android.trace.internal.DatadogSpanAdapter
import cloud.flashcat.android.trace.internal.DatadogSpanContextAdapter
import cloud.flashcat.android.trace.internal.DatadogTraceIdAdapter
import cloud.flashcat.android.trace.internal.DatadogTracerAdapter
import cloud.flashcat.android.trace.internal.DatadogTracerBuilderAdapter
import cloud.flashcat.android.trace.internal.DatadogTracingToolkit
import cloud.flashcat.android.trace.internal.domain.event.CoreTracerSpanToSpanEventMapper
import cloud.flashcat.trace.api.DDTraceId
import cloud.flashcat.trace.core.CoreTracer
import cloud.flashcat.trace.core.DDSpan
import cloud.flashcat.trace.core.DDSpanContext
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

fun DatadogSpan.resolveMeta(datadogContext: DatadogContext): JsonElement {
    val mapper = CoreTracerSpanToSpanEventMapper(false)
    val ddSpan = (this as DatadogSpanAdapter).delegate as DDSpan
    return mapper.resolveMeta(datadogContext, ddSpan).toJson()
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
