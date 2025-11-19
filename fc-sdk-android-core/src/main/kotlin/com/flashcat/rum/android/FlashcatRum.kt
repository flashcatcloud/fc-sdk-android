/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * 
 * Modifications (c) 2025 Flashcat (Beijing) Technology Co., Ltd.
 * This file has been created by Flashcat for use with the Flashcat RUM platform.
 */

package com.flashcat.rum.android

import android.content.Context
import android.util.Log
import com.datadog.android.Datadog
import com.datadog.android.api.context.UserInfo
import com.flashcat.rum.android.internal.FlashcatConfigAdapter
import com.datadog.android.privacy.TrackingConsent as InternalTrackingConsent

/**
 * Flashcat RUM SDK entry point.
 * 
 * This SDK is based on the open-source Datadog Android SDK (Apache 2.0).
 * It has been adapted to work with the Flashcat RUM platform.
 * 
 * @see <a href="https://github.com/DataDog/dd-sdk-android">Original Datadog Android SDK</a>
 * @see <a href="https://flashcat.cloud/docs/rum">Flashcat RUM Documentation</a>
 */
@Suppress("TooManyFunctions")
object FlashcatRum {

    // region Initialization

    /**
     * Initializes the Flashcat SDK.
     * 
     * @param context Your application context
     * @param configuration The configuration for the SDK library
     * @param trackingConsent Initial state of the tracking consent flag
     * @return The initialized SDK instance, or null if initialization fails
     * @throws IllegalArgumentException if the env name uses illegal characters (debug mode only)
     * 
     * @see FlashcatConfig
     * @see TrackingConsent
     */
    @JvmStatic
    @kotlin.internal.InlineOnly
    inline fun initialize(
        context: Context,
        configuration: FlashcatConfig,
        trackingConsent: TrackingConsent
    ): SdkCore? {
        val internalConsent = when (trackingConsent) {
            TrackingConsent.GRANTED -> InternalTrackingConsent.GRANTED
            TrackingConsent.NOT_GRANTED -> InternalTrackingConsent.NOT_GRANTED
            TrackingConsent.PENDING -> InternalTrackingConsent.PENDING
        }
        return Datadog.initialize(
            context, 
            FlashcatConfigAdapter.toDatadogConfiguration(configuration), 
            internalConsent
        )
    }

    /**
     * Initializes a named instance of the Flashcat SDK.
     * 
     * @param instanceName The name of the instance (stable across builds)
     * @param context Your application context
     * @param configuration The configuration for the SDK library
     * @param trackingConsent Initial state of the tracking consent flag
     * @return The initialized SDK instance, or null if initialization fails
     * 
     * @see FlashcatConfig
     * @see TrackingConsent
     */
    @JvmStatic
    @kotlin.internal.InlineOnly
    inline fun initialize(
        instanceName: String?,
        context: Context,
        configuration: FlashcatConfig,
        trackingConsent: TrackingConsent
    ): SdkCore? {
        val internalConsent = when (trackingConsent) {
            TrackingConsent.GRANTED -> InternalTrackingConsent.GRANTED
            TrackingConsent.NOT_GRANTED -> InternalTrackingConsent.NOT_GRANTED
            TrackingConsent.PENDING -> InternalTrackingConsent.PENDING
        }
        return Datadog.initialize(
            instanceName,
            context, 
            FlashcatConfigAdapter.toDatadogConfiguration(configuration), 
            internalConsent
        )
    }

    /**
     * Retrieve the initialized SDK instance.
     * 
     * @param instanceName The name of the instance to retrieve, or null for the default instance
     * @return The existing instance, or a no-op instance if not initialized
     */
    @JvmStatic
    @JvmOverloads
    @kotlin.internal.InlineOnly
    inline fun getInstance(instanceName: String? = null): SdkCore = 
        Datadog.getInstance(instanceName)

    /**
     * Check if SDK instance is initialized.
     * 
     * @param instanceName The name of the instance to check, or null for the default instance
     * @return True if the instance is initialized, false otherwise
     */
    @JvmStatic
    @JvmOverloads
    @kotlin.internal.InlineOnly
    inline fun isInitialized(instanceName: String? = null): Boolean = 
        Datadog.isInitialized(instanceName)

    /**
     * Stop the initialized SDK instance.
     * 
     * @param instanceName The name of the instance to stop, or null for the default instance
     */
    @JvmStatic
    @JvmOverloads
    @kotlin.internal.InlineOnly
    inline fun stopInstance(instanceName: String? = null) = 
        Datadog.stopInstance(instanceName)

    // endregion

    // region Tracking Consent

    /**
     * Set the tracking consent for data collection.
     * 
     * @param consent The tracking consent (GRANTED, NOT_GRANTED, or PENDING)
     * @param sdkCore SDK instance to set tracking consent in (default instance if not provided)
     * 
     * @see TrackingConsent
     */
    @JvmStatic
    @JvmOverloads
    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
    @kotlin.internal.InlineOnly
    inline fun setTrackingConsent(
        consent: TrackingConsent,
        sdkCore: SdkCore = getInstance()
    ) {
        val internalConsent = when (consent) {
            TrackingConsent.GRANTED -> InternalTrackingConsent.GRANTED
            TrackingConsent.NOT_GRANTED -> InternalTrackingConsent.NOT_GRANTED
            TrackingConsent.PENDING -> InternalTrackingConsent.PENDING
        }
        Datadog.setTrackingConsent(internalConsent, sdkCore)
    }

    // endregion

    // region User Information

    /**
     * Set user information.
     * 
     * @param id Unique user identifier (relevant to your business domain)
     * @param name User name or alias (nullable)
     * @param email User email (nullable)
     * @param extraInfo Additional information (nested up to 8 levels)
     * @param sdkCore SDK instance to set user info in (default instance if not provided)
     */
    @JvmStatic
    @JvmOverloads
    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
    @kotlin.internal.InlineOnly
    inline fun setUserInfo(
        id: String,
        name: String? = null,
        email: String? = null,
        extraInfo: Map<String, Any?> = emptyMap(),
        sdkCore: SdkCore = getInstance()
    ) = Datadog.setUserInfo(id, name, email, extraInfo, sdkCore)

    /**
     * Add custom properties to the user information.
     * 
     * @param properties Additional user properties
     * @param sdkCore SDK instance to add properties in (default instance if not provided)
     */
    @JvmStatic
    @JvmOverloads
    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
    @kotlin.internal.InlineOnly
    inline fun addUserProperties(
        properties: Map<String, Any?>,
        sdkCore: SdkCore = getInstance()
    ) = Datadog.addUserProperties(properties, sdkCore)

    /**
     * Clear all user information.
     * 
     * @param sdkCore SDK instance to clear user info from (default instance if not provided)
     */
    @JvmStatic
    @JvmOverloads
    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
    @kotlin.internal.InlineOnly
    inline fun clearUserInfo(sdkCore: SdkCore = getInstance()) = 
        Datadog.clearUserInfo(sdkCore)

    // endregion

    // region Account Information (B2B scenarios)

    /**
     * Set account information for B2B scenarios.
     * 
     * @param id Unique account identifier
     * @param name Account name (nullable)
     * @param extraInfo Additional account information
     * @param sdkCore SDK instance to set account info in (default instance if not provided)
     */
    @JvmStatic
    @JvmOverloads
    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
    @kotlin.internal.InlineOnly
    inline fun setAccountInfo(
        id: String,
        name: String? = null,
        extraInfo: Map<String, Any?> = emptyMap(),
        sdkCore: SdkCore = getInstance()
    ) = Datadog.setAccountInfo(id, name, extraInfo, sdkCore)

    /**
     * Add extra information to the account.
     * 
     * @param extraInfo Additional account properties
     * @param sdkCore SDK instance to add properties in (default instance if not provided)
     */
    @JvmStatic
    @JvmOverloads
    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
    @kotlin.internal.InlineOnly
    inline fun addAccountExtraInfo(
        extraInfo: Map<String, Any?>,
        sdkCore: SdkCore = getInstance()
    ) = Datadog.addAccountExtraInfo(extraInfo, sdkCore)

    /**
     * Clear all account information.
     * 
     * @param sdkCore SDK instance to clear account info from (default instance if not provided)
     */
    @JvmStatic
    @JvmOverloads
    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
    @kotlin.internal.InlineOnly
    inline fun clearAccountInfo(sdkCore: SdkCore = getInstance()) = 
        Datadog.clearAccountInfo(sdkCore)

    // endregion

    // region Debugging

    /**
     * Set the verbosity of the SDK.
     * 
     * Messages with a priority level equal or above the given level will be sent to Android's Logcat.
     * 
     * @param level One of the Android Log constants (Log.VERBOSE, Log.DEBUG, Log.INFO, Log.WARN, Log.ERROR, Log.ASSERT)
     */
    @JvmStatic
    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
    @kotlin.internal.InlineOnly
    inline fun setVerbosity(level: Int) = Datadog.setVerbosity(level)

    /**
     * Get the current verbosity level of the SDK.
     * 
     * @return One of the Android Log constants
     */
    @JvmStatic
    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
    @kotlin.internal.InlineOnly
    inline fun getVerbosity(): Int = Datadog.getVerbosity()

    /**
     * Clear all locally stored data.
     * 
     * This will delete all pending events that have not been uploaded yet.
     * 
     * @param sdkCore SDK instance to clear data from (default instance if not provided)
     */
    @JvmStatic
    @JvmOverloads
    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
    @kotlin.internal.InlineOnly
    inline fun clearAllData(sdkCore: SdkCore = getInstance()) = 
        Datadog.clearAllData(sdkCore)

    // endregion
}

