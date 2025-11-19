/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.okhttp.internal.utils

import com.flashcat.rum.log.LogAttributes
import com.flashcat.rum.trace.api.span.DatadogSpan

private const val HEX_RADIX = 16

internal object SpanSamplingIdProvider {

    fun provideId(span: DatadogSpan): ULong {
        val context = span.context()
        val sessionId = context.tags[LogAttributes.RUM_SESSION_ID] as? String

        // for a UUID with value aaaaaaaa-bbbb-Mccc-Nddd-1234567890ab
        // we use as the input id the last part : 0x1234567890ab
        val sessionIdToken = sessionId?.split('-')
            ?.lastOrNull()
            ?.toLongOrNull(HEX_RADIX)
            ?.toULong()

        return sessionIdToken ?: context.traceId.toLong().toULong()
    }
}
