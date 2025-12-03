/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.benchmark.sample.di.app

import cloud.flashcat.benchmark.sample.BenchmarkApplication
import cloud.flashcat.benchmark.sample.BenchmarkGlideModule
import cloud.flashcat.benchmark.sample.activities.LaunchActivity
import cloud.flashcat.benchmark.sample.di.activity.BenchmarkActivityComponentDependencies
import dagger.BindsInstance
import dagger.Component
import javax.inject.Singleton

@Component(
    modules = [
        AppModule::class,
        DatadogModule::class,
        NetworkModule::class,
        OpenTelemetryModule::class
    ]
)
@Singleton
internal interface BenchmarkAppComponent : BenchmarkActivityComponentDependencies {
    @Component.Factory
    interface Factory {
        fun create(
            @BindsInstance application: BenchmarkApplication
        ): BenchmarkAppComponent
    }

    fun inject(benchmarkApplication: BenchmarkApplication)
    fun inject(launchActivity: LaunchActivity)
    fun inject(glideModule: BenchmarkGlideModule)
}
