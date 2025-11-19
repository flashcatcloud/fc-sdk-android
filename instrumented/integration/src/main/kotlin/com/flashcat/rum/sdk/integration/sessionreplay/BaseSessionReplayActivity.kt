/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sdk.integration.sessionreplay

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.flashcat.rum.Flashcat
import com.flashcat.rum.rum.Rum
import com.flashcat.rum.rum.tracking.ActivityViewTrackingStrategy
import com.flashcat.rum.sdk.integration.RuntimeConfig
import com.flashcat.rum.sdk.utils.getForgeSeed
import com.flashcat.rum.sdk.utils.getSessionReplayPrivacy
import com.flashcat.rum.sdk.utils.getSrSampleRate
import com.flashcat.rum.sdk.utils.getTrackingConsent
import com.flashcat.rum.sessionreplay.SessionReplay
import com.flashcat.rum.sessionreplay.SessionReplayConfiguration
import com.flashcat.rum.sessionreplay.SessionReplayPrivacy
import java.util.Random

internal abstract class BaseSessionReplayActivity : AppCompatActivity() {
    @Suppress("CheckInternal")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = RuntimeConfig.configBuilder().build()
        val trackingConsent = intent.getTrackingConsent()
        val sessionReplayPrivacy = intent.getSessionReplayPrivacy()
        val sessionReplaySampleRate = intent.getSrSampleRate()
        Datadog.setVerbosity(Log.VERBOSE)
        // make sure the previous instance is stopped
        Datadog.stopInstance()
        val sdkCore = Datadog.initialize(this, config, trackingConsent)
        checkNotNull(sdkCore)
        val featureActivations = mutableListOf(
            {
                val rumConfig = RuntimeConfig.rumConfigBuilder()
                    .trackUserInteractions()
                    .trackLongTasks(RuntimeConfig.LONG_TASK_LARGE_THRESHOLD)
                    .useViewTrackingStrategy(ActivityViewTrackingStrategy(true))
                    .build()
                Rum.enable(rumConfig, sdkCore)
            },
            {
                val sessionReplayConfig = sessionReplayConfiguration(
                    sessionReplayPrivacy,
                    sessionReplaySampleRate
                )
                SessionReplay.enable(sessionReplayConfig, sdkCore)
            }
        )
        featureActivations.shuffled(Random(intent.getForgeSeed()))
            .forEach { it() }
        supportActionBar?.hide()
    }

    @Suppress("DEPRECATION")
    open fun sessionReplayConfiguration(privacy: SessionReplayPrivacy, sampleRate: Float): SessionReplayConfiguration {
        return RuntimeConfig.sessionReplayConfigBuilder(sampleRate)
            .setPrivacy(privacy)
            .build()
    }
}
