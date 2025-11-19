/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum

/**
 * Defines the Datadog sites you can send tracked data to.
 *
 * @param siteName Explicit site name property introduced in order to have a consistent SDK
 * instance ID (because this value is used there) in case if enum values are renamed.
 * @param intakeHostName the host name for the given site.
 */
enum class FlashcatSite private constructor(internal val siteName: String, private val intakeHostName: String) {

    /**
     *  The US1 site: [app.flashcat.cloud](https://app.flashcat.cloud).
     */
    US1("us1", "browser-intake-flashcat.cloud"),

    /**
     *  The US3 site: [us3.flashcat.cloud](https://us3.flashcat.cloud).
     */
    US3("us3"),

    /**
     *  The US5 site: [us5.flashcat.cloud](https://us5.flashcat.cloud).
     */
    US5("us5"),

    /**
     *  The EU1 site: [app.datadoghq.eu](https://app.datadoghq.eu).
     */
    EU1("eu1", "browser-intake-datadoghq.eu"),

    /**
     *  The AP1 site: [ap1.flashcat.cloud](https://ap1.flashcat.cloud).
     */
    AP1("ap1"),

    /**
     *  The AP2 site: [ap2.flashcat.cloud](https://ap2.flashcat.cloud).
     */
    AP2("ap2"),

    /**
     *  The US1_FED site (FedRAMP compatible): [app.ddog-gov.com](https://app.ddog-gov.com).
     */
    US1_FED("us1_fed", "browser-intake-ddog-gov.com"),

    /**
     *  The STAGING site (internal usage only): [app.datad0g.com](https://app.datad0g.com).
     */
    STAGING("staging", "browser-intake-datad0g.com");

    /**
     * Constructor using the generic way to build the intake endpoint host from the site name.
     * @param siteName Explicit site name property introduced in order to have a consistent SDK
     * instance ID (because this value is used there) in case if enum values are renamed.
     */
    private constructor(siteName: String) : this(
        siteName,
        "browser-intake-$siteName-flashcat.cloud"
    )

    /** The intake endpoint url. */
    val intakeEndpoint: String = "https://$intakeHostName"
}