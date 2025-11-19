/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.flags

import com.flashcat.rum.Flashcat
import com.flashcat.rum.api.SdkCore
import com.flashcat.rum.api.feature.FeatureSdkCore
import com.flashcat.rum.flags.internal.FlagsFeature

/**
 * Entry point for the Flags feature.
 */
object Flags {

    /**
     * Enables the Flags feature.
     *
     * @param configuration configuration to use with feature flags and experiments. If not provided, the default
     * configuration will be used.
     * @param sdkCore SDK instance to register feature in. If not provided, a default SDK instance
     * will be used.
     */
    @JvmOverloads
    @JvmStatic
    fun enable(
        configuration: FlagsConfiguration = FlagsConfiguration.default,
        sdkCore: SdkCore = Datadog.getInstance()
    ) {
        val flagsFeature = FlagsFeature(
            sdkCore = sdkCore as FeatureSdkCore,
            flagsConfiguration = configuration
        )

        sdkCore.registerFeature(flagsFeature)
    }
}
