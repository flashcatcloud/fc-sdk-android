/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.compose

import com.flashcat.rum.compose.internal.ComposeActionTrackingStrategy
import com.flashcat.rum.rum.RumConfiguration
import com.flashcat.rum.rum._RumInternalProxy

/**
 * Enable Jetpack Compose automatic actions tracking, such as
 * tap and scroll. Jetpack Compose actions tracking will be disabled if this API is not called,
 * which is the default behavior.
 *
 */
fun RumConfiguration.Builder.enableComposeActionTracking(): RumConfiguration.Builder {
    _RumInternalProxy.setComposeActionTrackingStrategy(
        builder = this,
        composeActionTrackingStrategy = ComposeActionTrackingStrategy()
    )
    return this
}
