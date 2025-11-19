/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sdk.rules

import android.app.Activity
import android.app.ActivityManager
import androidx.test.platform.app.InstrumentationRegistry
import com.flashcat.rum.Flashcat
import com.flashcat.rum.privacy.TrackingConsent
import com.flashcat.rum.rum.DdRumContentProvider
import com.flashcat.rum.rum.Rum
import com.flashcat.rum.rum.tracking.ActivityViewTrackingStrategy
import com.flashcat.rum.sdk.integration.RuntimeConfig

internal class GesturesTrackingActivityTestRule<T : Activity>(
    activityClass: Class<T>,
    keepRequests: Boolean = false,
    trackingConsent: TrackingConsent
) : RumMockServerActivityTestRule<T>(activityClass, keepRequests, trackingConsent) {

    override fun beforeActivityLaunched() {
        super.beforeActivityLaunched()
        val config = RuntimeConfig.configBuilder()
            .build()

        val sdkCore = Datadog.initialize(
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext,
            config,
            trackingConsent
        )
        checkNotNull(sdkCore)
        // we will use a large long task threshold to make sure we will not have LongTask events
        // noise in our integration tests.
        val rumConfig = RuntimeConfig.rumConfigBuilder()
            .trackUserInteractions()
            .trackLongTasks(RuntimeConfig.LONG_TASK_LARGE_THRESHOLD)
            .useViewTrackingStrategy(ActivityViewTrackingStrategy(false))
            .build()
        Rum.enable(rumConfig, sdkCore)
        DdRumContentProvider.processImportance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }
}
