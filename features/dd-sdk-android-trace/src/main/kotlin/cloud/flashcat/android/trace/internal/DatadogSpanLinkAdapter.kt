/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */
package cloud.flashcat.android.trace.internal

import cloud.flashcat.android.trace.api.span.DatadogSpanLink
import cloud.flashcat.trace.api.DDTraceId
import cloud.flashcat.trace.bootstrap.instrumentation.api.AgentSpanLink
import cloud.flashcat.trace.bootstrap.instrumentation.api.SpanLink
import cloud.flashcat.trace.bootstrap.instrumentation.api.SpanLinkAttributes

internal class DatadogSpanLinkAdapter(delegate: DatadogSpanLink) :
    SpanLink(
        /* traceId */
        DDTraceId.fromHex(delegate.traceId.toHexString()),
        /* spanId */
        delegate.spanId,
        /* traceFlags */
        if (delegate.sampled) AgentSpanLink.SAMPLED_FLAG else AgentSpanLink.DEFAULT_FLAGS,
        /* traceState */
        delegate.traceStrace,
        /* attributes */
        SpanLinkAttributes.fromMap(delegate.attributes)
    )
