/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.trace.opentelemetry.internal

import com.flashcat.rum.trace.api.scope.DatadogScope
import io.opentelemetry.context.Scope

internal class OtelScope(internal val scope: Scope, internal val delegate: DatadogScope) : Scope {
    override fun close() {
        delegate.close()
        scope.close()
    }
}
