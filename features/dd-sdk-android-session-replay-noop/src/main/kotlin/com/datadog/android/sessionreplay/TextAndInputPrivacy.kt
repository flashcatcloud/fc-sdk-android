/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

/**
 * Defines the Text and Input privacy level.
 */
enum class TextAndInputPrivacy : PrivacyLevel {
    /**
     * All the text will be masked.
     */
    MASK_ALL,

    /**
     * All the user input views will be masked.
     */
    MASK_ALL_INPUTS,

    /**
     * All the sensitive user input views will be masked.
     */
    MASK_SENSITIVE_INPUTS
}
