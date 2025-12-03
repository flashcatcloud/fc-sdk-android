/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */
package cloud.flashcat.android.trace.internal

import cloud.flashcat.android.trace.api.trace.DatadogTraceId
import cloud.flashcat.trace.api.DDTraceId

internal data class DatadogTraceIdAdapter(private val delegate: DDTraceId) : DatadogTraceId, DDTraceId() {
    override fun toLong(): Long = delegate.toLong()
    override fun toString(): String = delegate.toString()
    override fun toHexString(): String = delegate.toHexString()
    override fun toHighOrderLong(): Long = delegate.toHighOrderLong()
    override fun toHexStringPadded(size: Int): String = delegate.toHexStringPadded(size)
}
