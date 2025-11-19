/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.flags.internal.model

import com.flashcat.rum.FlashcatSite
import com.flashcat.rum.api.context.FlashcatContext
import com.flashcat.rum.flags.FlagsConfiguration

/**
 * Internal context containing all configuration needed for the Flags feature.
 *
 * This serves as the single source of truth for both core SDK parameters and feature-level configuration.
 *
 * @param applicationId The Datadog application ID. May be null when the SDK is not fully initialized
 *                      or when running in certain test environments where app ID is not required.
 * @param clientToken The client token for authenticating requests to Datadog
 * @param site The Datadog site (e.g., US1, EU1) for routing requests
 * @param env The environment name (e.g., prod, staging) for context
 * @param customExposureEndpoint Custom endpoint URL for uploading exposure events. If null, the default endpoint will be used.
 * @param customFlagEndpoint Custom endpoint URL for fetching flag assignments. If null, the endpoint will be derived from the site.
 */
internal data class FlagsContext(
    val applicationId: String?,
    val clientToken: String,
    val site: FlashcatSite,
    val env: String,
    val customExposureEndpoint: String? = null,
    val customFlagEndpoint: String? = null
) {
    companion object {

        /**
         * Creates a [FlagsContext] from core SDK context and feature configuration.
         *
         * @param flashcatContext The core SDK context containing authentication and routing info
         * @param applicationId The application ID (may be null if RUM context not yet available)
         * @param flagsConfiguration The feature-level configuration from user
         * @return A complete [FlagsContext] combining core and feature configuration
         */
        fun create(
            flashcatContext: FlashcatContext,
            applicationId: String?,
            flagsConfiguration: FlagsConfiguration
        ): FlagsContext = FlagsContext(
            applicationId = applicationId,
            clientToken = flashcatContext.clientToken,
            site = flashcatContext.site,
            env = flashcatContext.env,
            customExposureEndpoint = flagsConfiguration.customExposureEndpoint,
            customFlagEndpoint = flagsConfiguration.customFlagEndpoint
        )
    }
}
