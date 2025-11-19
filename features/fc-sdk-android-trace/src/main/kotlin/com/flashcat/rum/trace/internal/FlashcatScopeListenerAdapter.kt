/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.flashcat.rum.trace.internal

import com.flashcat.rum.trace.api.scope.DatadogScopeListener
import com.datadog.trace.api.scopemanager.ScopeListener

internal class DatadogScopeListenerAdapter(
    internal val delegate: DatadogScopeListener
) : ScopeListener {
    override fun afterScopeClosed() = delegate.afterScopeClosed()
    override fun afterScopeActivated() = delegate.afterScopeActivated()
}
