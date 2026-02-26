/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log

import com.datadog.android.Datadog
import com.datadog.android.api.SdkCore

/**
 * An entry point to Datadog Logs feature.
 */
@Suppress("UNUSED_PARAMETER")
object Logs {

    /**
     * Enables a Logs feature based on the configuration provided.
     *
     * @param logsConfiguration Configuration to use for the feature.
     * @param sdkCore SDK instance to register feature in. If not provided, default SDK instance
     * will be used.
     */
    @JvmOverloads
    @JvmStatic
    fun enable(logsConfiguration: LogsConfiguration, sdkCore: SdkCore = Datadog.getInstance()) {
    }

    /**
     * Identify whether a [Logs] has been enabled for the given SDK instance.
     *
     * This check is useful in scenarios where more than one component may be responsible
     * for enabling the feature
     *
     * @param sdkCore the [SdkCore] instance to check against. If not provided, default instance
     * will be checked.
     * @return whether Logs has been enabled
     */
    @JvmOverloads
    @JvmStatic
    fun isEnabled(sdkCore: SdkCore = Datadog.getInstance()): Boolean {
        return false
    }

    /**
     * Add a custom attribute to all future logs sent by loggers created from the given SDK core.
     *
     * Values can be nested up to 10 levels deep. Keys
     * using more than 10 levels will be sanitized by SDK.
     *
     * @param key the key for this attribute
     * @param value the attribute value
     * @param sdkCore the [SdkCore] instance to add the attribute to. If not provided, the default
     * instance is used.
     */
    @JvmOverloads
    @JvmStatic
    fun addAttribute(key: String, value: Any?, sdkCore: SdkCore = Datadog.getInstance()) {
    }

    /**
     * Remove a custom attribute from all future logs sent by loggers created from the given SDK core.
     *
     * Previous logs won't lose the attribute value associated with this key if they were created
     * prior to this call.
     *
     * @param key the key of the attribute to remove
     * @param sdkCore the [SdkCore] instance to remove the attribute from. If not provided, the default
     * instance is used.
     */
    @JvmOverloads
    @JvmStatic
    fun removeAttribute(key: String, sdkCore: SdkCore = Datadog.getInstance()) {
    }

    internal const val LOGS_NOT_ENABLED_MESSAGE =
        "You're trying to add attributes to logs, but the feature is not enabled. " +
            "Please enable it first."
}
