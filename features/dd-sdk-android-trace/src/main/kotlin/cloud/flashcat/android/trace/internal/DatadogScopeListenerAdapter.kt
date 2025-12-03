/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */
package cloud.flashcat.android.trace.internal

import cloud.flashcat.android.trace.api.scope.DatadogScopeListener
import cloud.flashcat.trace.api.scopemanager.ScopeListener

internal class DatadogScopeListenerAdapter(
    internal val delegate: DatadogScopeListener
) : ScopeListener {
    override fun afterScopeClosed() = delegate.afterScopeClosed()
    override fun afterScopeActivated() = delegate.afterScopeActivated()
}
