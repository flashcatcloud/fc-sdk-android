/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

/**
 * Defines the Session Replay privacy level.
 */
enum class SessionReplayPrivacy {
    /**
     * All the views will be masked.
     */
    MASK,

    /**
     * All the views will be visible.
     */
    ALLOW,

    /**
     * All the user input views will be masked.
     */
    MASK_USER_INPUT
}
