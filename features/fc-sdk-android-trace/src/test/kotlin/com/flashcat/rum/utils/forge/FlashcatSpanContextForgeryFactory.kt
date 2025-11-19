/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.flashcat.rum.utils.forge

import com.flashcat.rum.trace.api.span.DatadogSpanContext
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class DatadogSpanContextForgeryFactory : ForgeryFactory<DatadogSpanContext> {
    override fun getForgery(forge: Forge): DatadogSpanContext = mock {
        on { traceId } doReturn forge.getForgery()
        on { spanId } doReturn forge.aLong()
        on { samplingPriority } doReturn forge.anInt()
        on { tags } doReturn forge.aMap { aString() to aString() }
    }
}
