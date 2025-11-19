/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.flashcat.rum.rum.internal.tracking

import com.flashcat.rum.rum.internal.instrumentation.gestures.GesturesTracker
import com.flashcat.rum.rum.tracking.TrackingStrategy
import com.datadog.tools.annotation.NoOpImplementation

/**
 * A TrackingStrategy dedicated to user actions tracking.
 */
@NoOpImplementation
internal interface UserActionTrackingStrategy : TrackingStrategy {
    fun getGesturesTracker(): GesturesTracker
}
