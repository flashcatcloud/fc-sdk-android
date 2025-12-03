/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.tv.sample

import android.app.Application
import android.util.Log
import cloud.flashcat.android.Flashcat
import cloud.flashcat.android.FlashcatSite
import cloud.flashcat.android.core.configuration.BatchSize
import cloud.flashcat.android.core.configuration.Configuration
import cloud.flashcat.android.core.configuration.UploadFrequency
import cloud.flashcat.android.core.sampling.RateBasedSampler
import cloud.flashcat.android.log.Logger
import cloud.flashcat.android.log.Logs
import cloud.flashcat.android.log.LogsConfiguration
import cloud.flashcat.android.okhttp.DatadogEventListener
import cloud.flashcat.android.okhttp.DatadogInterceptor
import cloud.flashcat.android.okhttp.trace.TracingInterceptor
import cloud.flashcat.android.privacy.TrackingConsent
import cloud.flashcat.android.rum.GlobalRumMonitor
import cloud.flashcat.android.rum.Rum
import cloud.flashcat.android.rum.RumConfiguration
import cloud.flashcat.android.rum.tracking.ActivityViewTrackingStrategy
import cloud.flashcat.android.sessionreplay.ImagePrivacy
import cloud.flashcat.android.sessionreplay.SessionReplay
import cloud.flashcat.android.sessionreplay.SessionReplayConfiguration
import cloud.flashcat.android.sessionreplay.SystemRequirementsConfiguration
import cloud.flashcat.android.sessionreplay.TextAndInputPrivacy
import cloud.flashcat.android.sessionreplay.TouchPrivacy
import cloud.flashcat.android.sessionreplay.material.MaterialExtensionSupport
import cloud.flashcat.android.timber.DatadogTree
import cloud.flashcat.android.tv.sample.net.OkHttpDownloader
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
        Flashcat.setVerbosity(Log.VERBOSE)
        Flashcat.initialize(
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
            .useSite(FlashcatSite.CN)
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
