/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.trace

import cloud.flashcat.android.Flashcat
import cloud.flashcat.android.api.SdkCore
import cloud.flashcat.android.api.feature.FeatureSdkCore
import cloud.flashcat.android.trace.internal.TracingFeature

/**
 * An entry point to Datadog Traces feature.
 */
object Trace {

    /**
     * Enables a Traces feature based on the configuration provided.
     *
     * @param traceConfiguration Configuration to use for the feature.
     * @param sdkCore SDK instance to register feature in. If not provided, default SDK instance
     * will be used.
     */
    @JvmOverloads
    @JvmStatic
    fun enable(traceConfiguration: TraceConfiguration, sdkCore: SdkCore = Flashcat.getInstance()) {
        val tracingFeature = TracingFeature(
            sdkCore = sdkCore as FeatureSdkCore,
            customEndpointUrl = traceConfiguration.customEndpointUrl,
            spanEventMapper = traceConfiguration.eventMapper,
            networkInfoEnabled = traceConfiguration.networkInfoEnabled
        )

        sdkCore.registerFeature(tracingFeature)
    }
}
