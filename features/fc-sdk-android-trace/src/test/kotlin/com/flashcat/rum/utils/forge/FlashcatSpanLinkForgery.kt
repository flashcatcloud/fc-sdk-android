/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.flashcat.rum.utils.forge

import com.flashcat.rum.trace.api.span.DatadogSpanLink
import com.flashcat.rum.trace.api.trace.DatadogTraceId
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class DatadogSpanLinkForgery : ForgeryFactory<DatadogSpanLink> {
    override fun getForgery(forge: Forge): DatadogSpanLink = object : DatadogSpanLink {
        override val spanId: Long = forge.aLong()
        override val sampled: Boolean = forge.aBool()
        override val traceStrace: String = forge.aString()
        override val attributes: Map<String, String> = forge.aMap { aString() to aString() }
        override val traceId: DatadogTraceId = forge.getForgery()
    }
}
