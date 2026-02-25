/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

/**
 * Proxy for internal Session Replay functionality.
 */
@Suppress("UNUSED_PARAMETER")
class _SessionReplayInternalProxy(builder: SessionReplayConfiguration.Builder) {
    /**
     * Sets the internal callback for Session Replay.
     */
    fun setInternalCallback(callback: SessionReplayInternalCallback): SessionReplayConfiguration.Builder {
        return SessionReplayConfiguration.Builder()
    }
}
