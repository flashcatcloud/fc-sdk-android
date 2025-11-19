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
 * Defines the user's tracking consent status for data collection.
 */
enum class TrackingConsent {
    /**
     * Permission to collect data has been granted.
     * The SDK will collect and send all data to Flashcat.
     */
    GRANTED,

    /**
     * Permission to collect data has not been granted.
     * The SDK will not send any data to Flashcat.
     */
    NOT_GRANTED,

    /**
     * Permission to collect data is pending.
     * The SDK will collect data locally but not send it to Flashcat.
     * Data will be sent once consent changes to GRANTED, or deleted if consent changes to NOT_GRANTED.
     */
    PENDING
}

