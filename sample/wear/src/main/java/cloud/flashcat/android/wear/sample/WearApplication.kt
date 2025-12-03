/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.wear.sample

import android.app.Application
import android.util.Log
import cloud.flashcat.android.Flashcat
import cloud.flashcat.android.FlashcatSite
import cloud.flashcat.android.core.configuration.Configuration
import cloud.flashcat.android.log.Logs
import cloud.flashcat.android.log.LogsConfiguration
import cloud.flashcat.android.privacy.TrackingConsent
import cloud.flashcat.android.rum.Rum
import cloud.flashcat.android.rum.RumConfiguration
import cloud.flashcat.android.rum.tracking.ActivityViewTrackingStrategy
import cloud.flashcat.android.trace.DatadogTracing
import cloud.flashcat.android.trace.GlobalDatadogTracer
import cloud.flashcat.android.trace.Trace
import cloud.flashcat.android.trace.TraceConfiguration
import cloud.flashcat.android.trace.opentelemetry.DatadogOpenTelemetry
import io.opentelemetry.api.GlobalOpenTelemetry
import timber.log.Timber

/**
 * The main [Application] for the sample WearOs project.
 */
class WearApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initializeDatadog()
    }

    @Suppress("MagicNumber")
    private fun initializeDatadog() {
        Flashcat.setVerbosity(Log.VERBOSE)

        val sdkCore = Flashcat.initialize(
            this,
            createDatadogConfiguration(),
            TrackingConsent.GRANTED
        )

        Rum.enable(
            RumConfiguration.Builder(BuildConfig.DD_RUM_APPLICATION_ID)
                .setTelemetrySampleRate(100f)
                .useViewTrackingStrategy(ActivityViewTrackingStrategy(true))
                .trackUserInteractions()
                .trackLongTasks(250L)
                .apply {
                    if (BuildConfig.DD_OVERRIDE_RUM_URL.isNotBlank()) {
                        useCustomEndpoint(BuildConfig.DD_OVERRIDE_RUM_URL)
                    }
                }
                .build()
        )

        Logs.enable(
            LogsConfiguration.Builder()
                .apply {
                    if (BuildConfig.DD_OVERRIDE_LOGS_URL.isNotBlank()) {
                        useCustomEndpoint(BuildConfig.DD_OVERRIDE_LOGS_URL)
                    }
                }
                .build()
        )

        Trace.enable(
            TraceConfiguration.Builder()
                .apply {
                    if (BuildConfig.DD_OVERRIDE_TRACES_URL.isNotBlank()) {
                        useCustomEndpoint(BuildConfig.DD_OVERRIDE_TRACES_URL)
                    }
                }
                .build()
        )

        Flashcat.setUserInfo(
            id = "wear 42",
            name = null,
            email = null
        )

        GlobalDatadogTracer.registerIfAbsent(
            DatadogTracing.newTracerBuilder(checkNotNull(sdkCore))
                .withServiceName(BuildConfig.APPLICATION_ID)
                .build()
        )

        GlobalOpenTelemetry.set(
            DatadogOpenTelemetry(BuildConfig.APPLICATION_ID)
        )
    }

    private fun createDatadogConfiguration(): Configuration {
        val configBuilder = Configuration.Builder(
            clientToken = BuildConfig.DD_CLIENT_TOKEN,
            env = BuildConfig.BUILD_TYPE,
            variant = BuildConfig.FLAVOR
        )

        try {
            configBuilder.useSite(FlashcatSite.valueOf(BuildConfig.DD_SITE_NAME))
        } catch (e: IllegalArgumentException) {
            Timber.e("Error setting site to ${BuildConfig.DD_SITE_NAME}")
        }

        return configBuilder.build()
    }
}
