/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sdk.integration.rum

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.flashcat.rum.Flashcat
import com.flashcat.rum.log.Logs
import com.flashcat.rum.privacy.TrackingConsent
import com.flashcat.rum.rum.GlobalRumMonitor
import com.flashcat.rum.rum.Rum
import com.flashcat.rum.rum.tracking.ActivityViewTrackingStrategy
import com.flashcat.rum.sdk.integration.R
import com.flashcat.rum.sdk.integration.RuntimeConfig
import com.flashcat.rum.sdk.utils.getForgeSeed
import com.flashcat.rum.trace.Trace
import java.util.Random

internal class KioskSplashPlaygroundActivity : AppCompatActivity() {

    @Suppress("CheckInternal")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = RuntimeConfig.configBuilder().build()

        val sdkCore = Datadog.initialize(
            this,
            config,
            TrackingConsent.GRANTED
        )
        checkNotNull(sdkCore)
        val featureActivations = mutableListOf(
            {
                val rumConfig = RuntimeConfig.rumConfigBuilder()
                    .trackLongTasks(RuntimeConfig.LONG_TASK_LARGE_THRESHOLD)
                    .useViewTrackingStrategy(ActivityViewTrackingStrategy(false))
                    .disableUserInteractionTracking()
                    .build()
                Rum.enable(rumConfig, sdkCore)
            },
            { Logs.enable(RuntimeConfig.logsConfigBuilder().build(), sdkCore) },
            { Trace.enable(RuntimeConfig.tracesConfigBuilder().build(), sdkCore) }
        )
        featureActivations.shuffled(Random(intent.getForgeSeed())).forEach { it() }

        setContentView(R.layout.kiosk_splash_layout)

        val endSessionButton: Button = findViewById(R.id.end_session)
        endSessionButton.setOnClickListener {
            GlobalRumMonitor.get(sdkCore).stopSession()
        }

        val startKioskButton: Button = findViewById(R.id.start_kiosk)
        startKioskButton.setOnClickListener {
            startMainActivity()
        }
    }

    private fun startMainActivity() {
        val intent = Intent(this, KioskTrackedPlaygroundActivity::class.java)
        startActivity(intent)
    }
}
