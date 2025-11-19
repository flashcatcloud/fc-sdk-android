/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.trace.internal.coroutines

import com.flashcat.rum.trace.api.span.DatadogSpan
import com.flashcat.rum.trace.coroutines.CoroutineScopeSpan
import kotlinx.coroutines.CoroutineScope

internal class CoroutineScopeSpanImpl(
    private val scope: CoroutineScope,
    private val span: DatadogSpan
) : CoroutineScopeSpan,
    CoroutineScope by scope,
    DatadogSpan by span
