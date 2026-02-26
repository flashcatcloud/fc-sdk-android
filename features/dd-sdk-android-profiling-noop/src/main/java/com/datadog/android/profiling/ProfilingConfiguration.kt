/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling

/**
 * Describes configuration to be used for the Profiling feature.
 */
@ExperimentalProfilingApi
@Suppress("UNUSED_PARAMETER")
class ProfilingConfiguration internal constructor() {

    /**
     * A Builder class for a [ProfilingConfiguration].
     */
    class Builder {

        /**
         * Sets the sampling rate for Application Launch profiling.
         *
         * @param sampleRate The sample rate, expressed as a percentage between 0 and 100 (inclusive).
         */
        @Suppress("UNUSED_PARAMETER")
        fun setApplicationLaunchSampleRate(sampleRate: Float): Builder {
            return this
        }

        /**
         * Let the Profiling feature target a custom server.
         * @param endpoint the full endpoint url.
         */
        @Suppress("UNUSED_PARAMETER")
        fun useCustomEndpoint(endpoint: String): Builder {
            return this
        }

        /**
         * Builds a [ProfilingConfiguration] based on the current state of this Builder.
         */
        fun build(): ProfilingConfiguration = ProfilingConfiguration()
    }
}
