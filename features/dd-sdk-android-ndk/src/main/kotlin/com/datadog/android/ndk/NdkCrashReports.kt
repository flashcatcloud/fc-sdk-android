/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.ndk

import com.datadog.android.Datadog
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.ndk.internal.NdkCrashReportsFeature

/**
 * An entry point to Datadog NDK Crash Reports feature.
 */
object NdkCrashReports {

    /**
     * Enables a NDK Crash Reports feature.
     *
     * @param sdkCore SDK instance to register feature in. If not provided, default SDK instance
     * will be used.
     */
    @JvmOverloads
    @JvmStatic
    fun enable(sdkCore: SdkCore = Datadog.getInstance()) {
        enable(sdkCore, crashBeaconEnabled = false)
    }

    /**
     * FLASHCAT FORK - Enables a NDK Crash Reports feature, and chooses whether a native crash in the
     * foreground sends a crash beacon.
     *
     * The beacon is a few hundred bytes without stack, sent once the crash report is written and
     * before the crash goes on to the previous handler, so the crash is counted even if the app is
     * never opened again. The crashed thread waits for it at most one second (the RUM remote
     * configuration can set 0.5 to 2 seconds). RUM crash beacons are also off by default;
     * explicitly call `RumConfiguration.Builder.setCrashBeaconEnabled(true)` as well.
     *
     * Off by default. Off, native crashes are handled exactly as with [enable].
     *
     * @param sdkCore SDK instance to register feature in.
     * @param crashBeaconEnabled whether native crashes send a crash beacon.
     */
    @JvmStatic
    fun enable(sdkCore: SdkCore, crashBeaconEnabled: Boolean) {
        val ndkCrashReportsFeature = NdkCrashReportsFeature(sdkCore as FeatureSdkCore, crashBeaconEnabled)

        sdkCore.registerFeature(ndkCrashReportsFeature)
    }
}
