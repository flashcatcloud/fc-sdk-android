/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling

import android.content.Context
import com.datadog.android.Datadog
import com.datadog.android.api.SdkCore

/**
 * An entry point to Datadog Profiling feature.
 */
@ExperimentalProfilingApi
@Suppress("UNUSED_PARAMETER")
object Profiling {

    /**
     * Enables the profiling feature.
     *
     * @param configuration Configuration to use for the feature.
     * @param sdkCore SDK instance to register feature in. If not provided, default SDK instance
     * will be used.
     */
    @JvmStatic
    @JvmOverloads
    fun enable(
        configuration: ProfilingConfiguration = ProfilingConfiguration(),
        sdkCore: SdkCore = Datadog.getInstance()
    ) {}

    /**
     * Start profiling for a given SDK instance.
     *
     * @param context application context
     * @param sdkCore SDK instance to start profiling with. If not provided, default SDK instance.
     */
    @JvmStatic
    @JvmOverloads
    fun start(context: Context, sdkCore: SdkCore = Datadog.getInstance()) {}

    /**
     * Stop profiling for a given SDK instance.
     *
     * @param sdkCore SDK instance to stop profiling. If not provided, default SDK instance.
     */
    @JvmStatic
    @JvmOverloads
    fun stop(sdkCore: SdkCore = Datadog.getInstance()) {}

    /**
     * Identify whether a [Profiling] has been enabled for the given SDK instance.
     *
     * @param sdkCore the [SdkCore] instance to check against. If not provided, default instance
     * will be checked.
     * @return whether Profiling has been enabled
     */
    @JvmStatic
    @JvmOverloads
    fun isEnabled(sdkCore: SdkCore = Datadog.getInstance()): Boolean {
        return false
    }
}
