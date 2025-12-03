/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.sdk.rules

import android.app.Activity
import android.app.ActivityManager
import androidx.test.platform.app.InstrumentationRegistry
import cloud.flashcat.android.Datadog
import cloud.flashcat.android.privacy.TrackingConsent
import cloud.flashcat.android.rum.DdRumContentProvider
import cloud.flashcat.android.rum.Rum
import cloud.flashcat.android.rum.tracking.ActivityViewTrackingStrategy
import cloud.flashcat.android.sdk.integration.RuntimeConfig

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
