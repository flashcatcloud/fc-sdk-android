/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.sdk.integration.sessionreplay

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import cloud.flashcat.android.Flashcat
import cloud.flashcat.android.rum.Rum
import cloud.flashcat.android.rum.tracking.ActivityViewTrackingStrategy
import cloud.flashcat.android.sdk.integration.RuntimeConfig
import cloud.flashcat.android.sdk.utils.getForgeSeed
import cloud.flashcat.android.sdk.utils.getSessionReplayPrivacy
import cloud.flashcat.android.sdk.utils.getSrSampleRate
import cloud.flashcat.android.sdk.utils.getTrackingConsent
import cloud.flashcat.android.sessionreplay.SessionReplay
import cloud.flashcat.android.sessionreplay.SessionReplayConfiguration
import cloud.flashcat.android.sessionreplay.SessionReplayPrivacy
import java.util.Random

internal abstract class BaseSessionReplayActivity : AppCompatActivity() {
    @Suppress("CheckInternal")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = RuntimeConfig.configBuilder().build()
        val trackingConsent = intent.getTrackingConsent()
        val sessionReplayPrivacy = intent.getSessionReplayPrivacy()
        val sessionReplaySampleRate = intent.getSrSampleRate()
        Flashcat.setVerbosity(Log.VERBOSE)
        // make sure the previous instance is stopped
        Flashcat.stopInstance()
        val sdkCore = Flashcat.initialize(this, config, trackingConsent)
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
