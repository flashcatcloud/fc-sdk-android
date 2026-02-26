/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

/**
 * Describes configuration to be used for the Flags feature.
 */
@Suppress("UNUSED_PARAMETER")
class FlagsConfiguration internal constructor() {
    /**
     * A Builder class for a [FlagsConfiguration].
     */
    class Builder {
        /**
         * Sets whether exposures should be logged.
         * @param enabled Whether to enable exposure logging.
         */
        fun trackExposures(enabled: Boolean): Builder = this

        /**
         * Sets a custom endpoint URL for sending exposure events.
         * @param endpoint The custom endpoint URL.
         */
        fun useCustomExposureEndpoint(endpoint: String): Builder = this

        /**
         * Sets a custom endpoint URL for fetching precomputed flag assignments.
         * @param endpoint The full endpoint URL.
         */
        fun useCustomFlagEndpoint(endpoint: String): Builder = this

        /**
         * Sets whether RUM evaluation logging is enabled.
         * @param enabled whether flag evaluations are added to views in RUM.
         */
        fun rumIntegrationEnabled(enabled: Boolean): Builder = this

        /**
         * Configures error handling behavior in debug builds.
         * @param enabled Whether to enable graceful mode in debug builds.
         */
        fun gracefulModeEnabled(enabled: Boolean): Builder = this

        /**
         * Builds a [FlagsConfiguration] based on the current state of this Builder.
         */
        fun build(): FlagsConfiguration = FlagsConfiguration()
    }

    /**
     * Companion object for [FlagsConfiguration] providing factory methods and default instances.
     */
    companion object {
        internal val default = Builder().build()
    }
}
