/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.rum.internal

import com.flashcat.rum.rum.RumSessionListener

internal class NoOpRumSessionListener : RumSessionListener {
    override fun onSessionStarted(sessionId: String, isDiscarded: Boolean) {
        // no-op
    }

    override fun equals(other: Any?): Boolean {
        return other != null && other is NoOpRumSessionListener
    }

    override fun hashCode(): Int {
        return 0
    }
}
