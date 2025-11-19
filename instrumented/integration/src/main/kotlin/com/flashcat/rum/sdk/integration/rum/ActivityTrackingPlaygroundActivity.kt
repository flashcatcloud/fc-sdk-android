/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sdk.integration.rum

import android.app.ActivityManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.flashcat.rum.Flashcat
import com.flashcat.rum.rum.DdRumContentProvider
import com.flashcat.rum.rum.Rum
import com.flashcat.rum.rum.tracking.ActivityViewTrackingStrategy
import com.flashcat.rum.sdk.integration.R
import com.flashcat.rum.sdk.integration.RuntimeConfig
import com.flashcat.rum.sdk.utils.getTrackingConsent

internal class ActivityTrackingPlaygroundActivity : AppCompatActivity() {

    @Suppress("CheckInternal")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // we will use a large long task threshold to make sure we will not have LongTask events
        // noise in our integration tests.
        val config = RuntimeConfig.configBuilder().build()
        val trackingConsent = intent.getTrackingConsent()

        Datadog.setVerbosity(Log.VERBOSE)
        val sdkCore = Datadog.initialize(this, config, trackingConsent)
        checkNotNull(sdkCore)

        DdRumContentProvider.processImportance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND

        val rumConfig = RuntimeConfig.rumConfigBuilder()
            .trackUserInteractions()
            .trackLongTasks(RuntimeConfig.LONG_TASK_LARGE_THRESHOLD)
            .useViewTrackingStrategy(ActivityViewTrackingStrategy(true))
            .build()
        Rum.enable(rumConfig, sdkCore)
        setContentView(R.layout.fragment_tracking_layout)
    }
}
