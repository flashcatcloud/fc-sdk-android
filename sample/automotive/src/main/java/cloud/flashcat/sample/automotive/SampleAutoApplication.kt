/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.sample.automotive

import android.app.Application
import android.util.Log
import cloud.flashcat.android.Datadog
import cloud.flashcat.android.FlashcatSite
import cloud.flashcat.android.core.configuration.BatchSize
import cloud.flashcat.android.core.configuration.Configuration
import cloud.flashcat.android.core.configuration.UploadFrequency
import cloud.flashcat.android.log.Logs
import cloud.flashcat.android.log.LogsConfiguration
import cloud.flashcat.android.privacy.TrackingConsent
import cloud.flashcat.android.rum.GlobalRumMonitor
import cloud.flashcat.android.rum.Rum
import cloud.flashcat.android.rum.RumConfiguration
import cloud.flashcat.android.rum.tracking.ActivityViewTrackingStrategy

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
            .useSite(FlashcatSite.CN)
            .setBatchSize(BatchSize.SMALL)
            .setUploadFrequency(UploadFrequency.FREQUENT)
            .build()
    }

    private companion object {
        private const val FULL_SAMPLING_RATE = 100f
    }
}
