/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.trace.integration.otel

import cloud.flashcat.android.trace.api.span.DatadogSpan
import cloud.flashcat.tools.unit.getFieldValue
import io.opentelemetry.api.trace.Span

internal fun Span.leastSignificant64BitsTraceIdAsHex(): String {
    return spanContext.traceId.takeLast(16)
}

internal fun Span.mostSignificant64BitsTraceIdAsHex(): String {
    return spanContext.traceId.take(16)
}

internal fun Span.spanIdAsHex(): String {
    return spanContext.spanId
}

internal fun Span.expectedSpanName(): String {
    val datadogSpan: DatadogSpan = this.getFieldValue("delegate")
    return datadogSpan.operationName
}
