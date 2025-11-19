/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.trace.coroutines

import com.flashcat.rum.trace.api.span.DatadogSpan
import kotlinx.coroutines.CoroutineScope

/**
 * An object that implements both [DatadogSpan] and [CoroutineScope].
 */
interface CoroutineScopeSpan : CoroutineScope, DatadogSpan
