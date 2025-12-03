/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.benchmark.sample.di.app

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import cloud.flashcat.android.Datadog
import cloud.flashcat.android.api.SdkCore
import cloud.flashcat.android.core.configuration.BackPressureMitigation
import cloud.flashcat.android.core.configuration.BackPressureStrategy
import cloud.flashcat.android.core.configuration.BatchSize
import cloud.flashcat.android.core.configuration.Configuration
import cloud.flashcat.android.core.configuration.UploadFrequency
import cloud.flashcat.android.log.Logger
import cloud.flashcat.android.privacy.TrackingConsent
import cloud.flashcat.android.rum.GlobalRumMonitor
import cloud.flashcat.android.rum.RumMonitor
import cloud.flashcat.benchmark.DatadogBaseMeter
import cloud.flashcat.benchmark.DatadogExporterConfiguration
import cloud.flashcat.benchmark.DatadogSdkMeter
import cloud.flashcat.benchmark.DatadogVitalsMeter
import cloud.flashcat.benchmark.sample.config.BenchmarkConfig
import cloud.flashcat.benchmark.sample.config.SyntheticsRun
import cloud.flashcat.benchmark.sample.config.SyntheticsScenario
import cloud.flashcat.sample.benchmark.BuildConfig
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
internal interface DatadogModule {
    companion object {
        @Provides
        @Singleton
        fun provideSdkCore(
            context: Context,
            config: BenchmarkConfig
        ): SdkCore {
            if (config.run == SyntheticsRun.Baseline) {
                return Datadog.getInstance() // returns NoOpInternalSdkCore under the hood
            }

            return Datadog.initialize(
                context,
                createDatadogConfiguration(),
                TrackingConsent.GRANTED
            )!!
        }

        @Provides
        @Singleton
        fun provideDatadogMeter(config: BenchmarkConfig): DatadogBaseMeter {
            val exporterConfig = DatadogExporterConfiguration.Builder(BuildConfig.BENCHMARK_API_KEY)
                .setApplicationId(BuildConfig.APPLICATION_ID)
                .setApplicationName(BENCHMARK_APPLICATION_NAME)
                .setRun(config.getRun())
                .setScenario(config.getScenario())
                .setApplicationVersion(BuildConfig.VERSION_NAME)
                .setIntervalInSeconds(METER_INTERVAL_IN_SECONDS)
                .build()

            return if (config.scenario == SyntheticsScenario.Upload) {
                DatadogSdkMeter.create(exporterConfig)
            } else {
                DatadogVitalsMeter.create(exporterConfig)
            }
        }

        @Provides
        @Singleton
        fun provideLogger(sdkCore: SdkCore): Logger {
            return Logger.Builder(sdkCore)
                .setName("benchmarkLogger")
                .setLogcatLogsEnabled(true)
                .build()
        }

        @Provides
        @Singleton
        fun provideRumMonitor(sdkCore: SdkCore): RumMonitor {
            return GlobalRumMonitor.get(sdkCore = sdkCore)
        }
    }
}

@SuppressLint("LogNotTimber")
private fun createDatadogConfiguration(): Configuration {
    val configBuilder = Configuration.Builder(
        clientToken = BuildConfig.BENCHMARK_CLIENT_TOKEN,
        env = BuildConfig.BUILD_TYPE
    )
        .setBatchSize(BatchSize.SMALL)
        .setUploadFrequency(UploadFrequency.FREQUENT)

    configBuilder.setBackpressureStrategy(
        BackPressureStrategy(
            CAPACITY_BACK_PRESSURE_STRATEGY,
            { Log.w("BackPressure", "Threshold reached") },
            { Log.e("BackPressure", "Item dropped: $it") },
            BackPressureMitigation.IGNORE_NEWEST
        )
    )

    return configBuilder.build()
}

// the same as the default one
private const val CAPACITY_BACK_PRESSURE_STRATEGY = 1024

private const val METER_INTERVAL_IN_SECONDS = 10L
private const val BENCHMARK_APPLICATION_NAME = "Benchmark Application"
