/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.flashcat.rum.utils.forge

import com.flashcat.rum.trace.api.trace.DatadogTraceId
import com.flashcat.rum.trace.internal.DatadogTraceIdAdapter
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

class DatadogTraceIdForgeryFactory : ForgeryFactory<DatadogTraceId> {
    override fun getForgery(forge: Forge): DatadogTraceId {
        return DatadogTraceIdAdapter(forge.getForgery())
    }
}
