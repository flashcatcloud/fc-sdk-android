/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

import com.datadog.android.flags.model.FlagsClientState

/**
 * Interface for observing [FlagsClient] state.
 */
interface StateObservable {
    /**
     * Returns the current state of the client.
     */
    fun getCurrentState(): FlagsClientState

    /**
     * Adds a listener to be notified of state changes.
     */
    fun addListener(listener: FlagsStateListener)

    /**
     * Removes a previously added listener.
     */
    fun removeListener(listener: FlagsStateListener)
}
