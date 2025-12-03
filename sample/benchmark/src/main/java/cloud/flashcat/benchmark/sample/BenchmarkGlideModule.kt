/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.benchmark.sample

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import cloud.flashcat.android.glide.DatadogGlideModule
import okhttp3.OkHttpClient
import javax.inject.Inject

@GlideModule
internal class BenchmarkGlideModule : DatadogGlideModule() {
    @Inject
    lateinit var okHttpClient: OkHttpClient

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        context.benchmarkAppComponent.inject(this)

        super.registerComponents(context, glide, registry)
    }

    override fun getClientBuilder(): OkHttpClient.Builder {
        return okHttpClient.newBuilder()
    }
}
