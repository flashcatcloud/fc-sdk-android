/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */
package cloud.flashcat.android.trace.internal

import cloud.flashcat.android.lint.InternalApi
import cloud.flashcat.android.trace.api.trace.DatadogTraceId
import cloud.flashcat.trace.api.DDTraceId

/**
 * For Datadog internal use only.
 *
 * Converts a hexadecimal string representation of a trace ID into a [DatadogTraceId] instance.
 *
 * @param traceId The hexadecimal string representation of the trace ID to be converted.
 * @return The corresponding [DatadogTraceId] instance.
 */
@InternalApi
fun DatadogTraceId.Companion.fromHex(traceId: String): DatadogTraceId {
    return DatadogTraceIdAdapter(DDTraceId.fromHex(traceId))
}
