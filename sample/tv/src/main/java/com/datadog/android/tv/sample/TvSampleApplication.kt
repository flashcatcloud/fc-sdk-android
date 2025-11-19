/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.tv.sample

import android.app.Application
import android.util.Log
import com.flashcat.rum.Flashcat
import com.flashcat.rum.FlashcatSite
import com.flashcat.rum.core.configuration.BatchSize
import com.flashcat.rum.core.configuration.Configuration
import com.flashcat.rum.core.configuration.UploadFrequency
import com.flashcat.rum.core.sampling.RateBasedSampler
import com.flashcat.rum.log.Logger
import com.flashcat.rum.log.Logs
import com.flashcat.rum.log.LogsConfiguration
import com.flashcat.rum.okhttp.DatadogEventListener
import com.flashcat.rum.okhttp.DatadogInterceptor
import com.flashcat.rum.okhttp.trace.TracingInterceptor
import com.flashcat.rum.privacy.TrackingConsent
import com.flashcat.rum.rum.GlobalRumMonitor
import com.flashcat.rum.rum.Rum
import com.flashcat.rum.rum.RumConfiguration
import com.flashcat.rum.rum.tracking.ActivityViewTrackingStrategy
import com.flashcat.rum.sessionreplay.ImagePrivacy
import com.flashcat.rum.sessionreplay.SessionReplay
import com.flashcat.rum.sessionreplay.SessionReplayConfiguration
import com.flashcat.rum.sessionreplay.SystemRequirementsConfiguration
import com.flashcat.rum.sessionreplay.TextAndInputPrivacy
import com.flashcat.rum.sessionreplay.TouchPrivacy
import com.flashcat.rum.sessionreplay.material.MaterialExtensionSupport
import com.flashcat.rum.timber.DatadogTree
import com.flashcat.rum.tv.sample.net.OkHttpDownloader
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import timber.log.Timber

/**
 * The main [Application] for the sample TV project.
 */
class TvSampleApplication : Application() {

    internal lateinit var okHttpClient: OkHttpClient

    override fun onCreate() {
        super.onCreate()
        initializeDatadog()
        initializeTimber()
        initializeOkHttp()
        initializeNewPipe()
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

        val sessionReplayConfig = createSessionReplayConfiguration()
        SessionReplay.enable(sessionReplayConfig)

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

    private fun createSessionReplayConfiguration(): SessionReplayConfiguration {
        return SessionReplayConfiguration.Builder(FULL_SAMPLING_RATE)
            .setImagePrivacy(ImagePrivacy.MASK_ALL)
            .setTouchPrivacy(TouchPrivacy.SHOW)
            .setTextAndInputPrivacy(TextAndInputPrivacy.MASK_SENSITIVE_INPUTS)
            .addExtensionSupport(MaterialExtensionSupport())
            .setSystemRequirements(SystemRequirementsConfiguration.NONE)
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

    @Suppress("TooGenericExceptionCaught", "CheckInternal")
    private fun initializeTimber() {
        val logger = Logger.Builder()
            .setName("timber")
            .setNetworkInfoEnabled(true)
            .setLogcatLogsEnabled(true)
            .build()

        Timber.plant(DatadogTree(logger))
    }

    private fun initializeOkHttp() {
        okHttpClient = OkHttpClient.Builder()
            .addInterceptor(
                DatadogInterceptor.Builder(emptyMap())
                    .setTraceSampler(RateBasedSampler(FULL_SAMPLING_RATE))
                    .build()
            )
            .addNetworkInterceptor(
                TracingInterceptor.Builder(emptyMap())
                    .setTraceSampler(RateBasedSampler(FULL_SAMPLING_RATE))
                    .build()
            )
            .eventListenerFactory(DatadogEventListener.Factory())
            .build()
    }

    private fun initializeNewPipe() {
        NewPipe.init(OkHttpDownloader(okHttpClient))
    }

    companion object {
        private const val FULL_SAMPLING_RATE = 100f
    }
}
