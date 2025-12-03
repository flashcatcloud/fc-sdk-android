/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.trace.api

import cloud.flashcat.trace.api.DDTraceId
import cloud.flashcat.trace.api.IdGenerationStrategy

class TestIdGenerationStrategy(
    val traceIds: List<Long>? = null,
    val spanIds: List<Long>? = null
) : IdGenerationStrategy(false) {

    private var index: Int = -1
    private val fallbackDelegate = fromName("SECURE_RANDOM")

    override fun generateTraceId(): DDTraceId = if (traceIds == null || traceIds.isEmpty()) {
        fallbackDelegate.generateTraceId()
    } else {
        DDTraceId.from(traceIds[++index % traceIds.size])
    }

    override fun generateSpanId() = if (spanIds == null || spanIds.isEmpty()) {
        fallbackDelegate.generateSpanId()
    } else {
        spanIds[++index % spanIds.size]
    }

    override fun getNonZeroPositiveLong(): Long = 1
}
