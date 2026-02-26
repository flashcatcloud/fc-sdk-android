/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

/**
 * Defines the Image privacy level.
 */
enum class ImagePrivacy : PrivacyLevel {
    /**
     * All the images will be masked.
     */
    MASK_ALL,

    /**
     * All the images will be masked, except for the ones that are smaller than a certain threshold.
     */
    MASK_LARGE_ONLY,

    /**
     * No images will be masked.
     */
    MASK_NONE
}
