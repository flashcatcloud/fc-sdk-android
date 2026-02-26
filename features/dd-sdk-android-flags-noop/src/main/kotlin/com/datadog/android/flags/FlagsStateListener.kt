/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

import com.datadog.android.flags.model.FlagsClientState

/**
 * Listener interface for observing [FlagsClient] state changes.
 */
fun interface FlagsStateListener {
    /**
     * Called when the client state changes.
     * @param newState The new operational state of the client.
     */
    fun onStateChanged(newState: FlagsClientState)
}
