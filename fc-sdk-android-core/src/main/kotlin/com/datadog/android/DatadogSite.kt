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
     *  The US1 site: FlashCat US region.
     */
    US1("us1", "api.flashcat.cloud"),

    /**
     *  The US3 site: FlashCat US3 region.
     */
    US3("us3", "api-us3.flashcat.cloud"),

    /**
     *  The US5 site: FlashCat US5 region.
     */
    US5("us5", "api-us5.flashcat.cloud"),

    /**
     *  The EU1 site: FlashCat EU region.
     */
    EU1("eu1", "api-eu.flashcat.cloud"),

    /**
     *  The AP1 site: FlashCat AP1 region.
     */
    AP1("ap1", "api-ap1.flashcat.cloud"),

    /**
     *  The AP2 site: FlashCat AP2 region (China optimized).
     */
    AP2("ap2", "api-cn.flashcat.cloud"),

    /**
     *  The US1_FED site (FedRAMP compatible): FlashCat FedRAMP.
     */
    US1_FED("us1_fed", "api-fed.flashcat.cloud"),

    /**
     *  The STAGING site (internal usage only): FlashCat staging environment.
     */
    STAGING("staging", "api-staging.flashcat.cloud");

    /**
     * Constructor using the generic way to build the intake endpoint host from the site name.
     * @param siteName Explicit site name property introduced in order to have a consistent SDK
     * instance ID (because this value is used there) in case if enum values are renamed.
     */
    private constructor(siteName: String) : this(
        siteName,
        "api-$siteName.flashcat.cloud"
    )

    /** The intake endpoint url. */
    val intakeEndpoint: String = "https://$intakeHostName"
}
