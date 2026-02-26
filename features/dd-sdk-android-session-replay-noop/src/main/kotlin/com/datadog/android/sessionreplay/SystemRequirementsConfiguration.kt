/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

/**
 * Describes system requirements configuration to be used for the Session Replay feature.
 */
@Suppress("UNUSED_PARAMETER")
class SystemRequirementsConfiguration internal constructor() {

    /**
     * A Builder class for a [SystemRequirementsConfiguration].
     */
    class Builder {

        /**
         * Sets the minimum RAM size requirement for this feature.
         * @param minRAMSizeMb the minimum RAM size requirement, in megabytes.
         */
        fun setMinRAMSizeMb(minRAMSizeMb: Int): Builder {
            return this
        }

        /**
         * Sets the minimum CPU core number requirement for this feature.
         * @param minCPUCoreNumber the minimum CPU core number requirement.
         */
        fun setMinCPUCoreNumber(minCPUCoreNumber: Int): Builder {
            return this
        }

        /**
         * Builds a [SystemRequirementsConfiguration] based on the current state of this Builder.
         */
        fun build(): SystemRequirementsConfiguration {
            return SystemRequirementsConfiguration()
        }
    }

    companion object {
        /**
         * Basic system requirements configuration.
         */
        @JvmStatic
        val BASIC: SystemRequirementsConfiguration = SystemRequirementsConfiguration()

        /**
         * No system requirements configuration.
         */
        @JvmStatic
        val NONE: SystemRequirementsConfiguration = SystemRequirementsConfiguration()
    }
}
