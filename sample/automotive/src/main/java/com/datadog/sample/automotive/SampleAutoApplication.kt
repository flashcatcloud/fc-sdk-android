/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.sample.automotive

import android.app.Application
import android.util.Log
import com.flashcat.rum.Flashcat
import com.flashcat.rum.FlashcatSite
import com.flashcat.rum.core.configuration.BatchSize
import com.flashcat.rum.core.configuration.Configuration
import com.flashcat.rum.core.configuration.UploadFrequency
import com.flashcat.rum.log.Logs
import com.flashcat.rum.log.LogsConfiguration
import com.flashcat.rum.privacy.TrackingConsent
import com.flashcat.rum.rum.GlobalRumMonitor
import com.flashcat.rum.rum.Rum
import com.flashcat.rum.rum.RumConfiguration
import com.flashcat.rum.rum.tracking.ActivityViewTrackingStrategy

@Suppress("UndocumentedPublicClass")
class SampleAutoApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initializeDatadog()
    }

    private fun initializeDatadog() {
        Datadog.setVerbosity(Log.VERBOSE)
        Datadog.initialize(
            this,
            createDatadogConfiguration(),
            TrackingConsent.GRANTED
        )

        val rumConfig = createRumConfiguration()
        Rum.enable(rumConfig)

        val logsConfig = LogsConfiguration.Builder().build()
        Logs.enable(logsConfig)

        GlobalRumMonitor.get().debug = true
    }

    private fun createRumConfiguration(): RumConfiguration {
        return RumConfiguration.Builder(BuildConfig.DD_RUM_APPLICATION_ID)
            .useViewTrackingStrategy(
                ActivityViewTrackingStrategy(true)
            )
            .setTelemetrySampleRate(FULL_SAMPLING_RATE)
            .trackUserInteractions()
            .build()
    }
    private fun createDatadogConfiguration(): Configuration {
        return Configuration.Builder(
            clientToken = BuildConfig.DD_CLIENT_TOKEN,
            env = "test",
            variant = ""
        )
            .useSite(FlashcatSite.US1)
            .setBatchSize(BatchSize.SMALL)
            .setUploadFrequency(UploadFrequency.FREQUENT)
            .build()
    }

    private companion object {
        private const val FULL_SAMPLING_RATE = 100f
    }
}
