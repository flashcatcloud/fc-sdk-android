/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package com.datadog.android

/**
 * Defines the Datadog sites you can send tracked data to.
 *
 * @param siteName Explicit site name property introduced in order to have a consistent SDK
 * instance ID (because this value is used there) in case if enum values are renamed.
 * @param intakeHostName the host name for the given site.
 */
enum class DatadogSite private constructor(internal val siteName: String, private val intakeHostName: String) {

    /**
     * The CN site: [browser.flashcat.cloud](https://browser.flashcat.cloud).
     */
    CN("cn", "browser.flashcat.cloud"),

    /**
     * The STAGING site (internal usage only): [jira.flashcat.cloud](https://jira.flashcat.cloud).
     */
    STAGING("staging", "jira.flashcat.cloud");

    /** The intake endpoint url. */
    val intakeEndpoint: String = "https://$intakeHostName"
}
