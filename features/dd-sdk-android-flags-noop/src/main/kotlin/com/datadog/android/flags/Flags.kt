/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

import com.datadog.android.Datadog
import com.datadog.android.api.SdkCore

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
    @Suppress("UNUSED_PARAMETER")
    @JvmOverloads
    @JvmStatic
    fun enable(
        configuration: FlagsConfiguration = FlagsConfiguration.default,
        sdkCore: SdkCore = Datadog.getInstance()
    ) {
        // no-op
    }
}
