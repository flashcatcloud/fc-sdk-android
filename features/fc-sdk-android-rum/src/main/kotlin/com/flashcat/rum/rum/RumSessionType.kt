/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.rum

import com.flashcat.rum.lint.InternalApi

/**
 * Enum representing the RUM session type.
 */
@InternalApi
enum class RumSessionType {
    /**
     * Synthetic session type.
     */
    SYNTHETICS,

    /**
     * User session type.
     */
    USER
}
