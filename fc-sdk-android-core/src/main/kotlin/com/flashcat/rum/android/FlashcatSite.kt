/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * 
 * Modifications (c) 2025 Flashcat (Beijing) Technology Co., Ltd.
 * This file has been created by Flashcat for use with the Flashcat RUM platform.
 */

package com.flashcat.rum.android

/**
 * Defines the Flashcat sites you can send tracked data to.
 *
 * @param siteName Explicit site name property for consistent SDK instance ID.
 * @param intakeHostName The host name for the given site.
 */
enum class FlashcatSite private constructor(
    internal val siteName: String,
    private val intakeHostName: String
) {

    /**
     * The Production site: [https://browser.flashcat.cloud](https://browser.flashcat.cloud).
     * 
     * This is the default and only predefined site.
     * For testing or custom deployments, use [FlashcatConfig.Builder.useCustomEndpoint].
     */
    PRODUCTION("production", "browser.flashcat.cloud");

    /** The intake endpoint base URL. */
    val intakeEndpoint: String = "https://$intakeHostName"
    
    /**
     * RUM intake endpoint.
     * Format: {intakeEndpoint}/api/v2/rum
     */
    val rumIntakeUrl: String
        get() = "$intakeEndpoint/api/v2/rum"
    
    /**
     * Logs intake endpoint.
     * Format: {intakeEndpoint}/api/v2/logs
     */
    val logsIntakeUrl: String
        get() = "$intakeEndpoint/api/v2/logs"
    
    /**
     * Trace intake endpoint.
     * Format: {intakeEndpoint}/api/v2/traces
     */
    val traceIntakeUrl: String
        get() = "$intakeEndpoint/api/v2/traces"
    
    /**
     * Session Replay intake endpoint.
     * Format: {intakeEndpoint}/api/v2/replay
     */
    val sessionReplayIntakeUrl: String
        get() = "$intakeEndpoint/api/v2/replay"
}

