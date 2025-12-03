/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.trace.opentelemetry.internal

import cloud.flashcat.android.trace.api.scope.DatadogScope
import io.opentelemetry.context.Scope

internal class OtelScope(internal val scope: Scope, internal val delegate: DatadogScope) : Scope {
    override fun close() {
        delegate.close()
        scope.close()
    }
}
