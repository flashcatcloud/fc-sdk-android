/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import android.app.Activity

/**
 * Internal callback for Session Replay.
 */
interface SessionReplayInternalCallback {
    /**
     * Adds a resource item to the resource queue.
     */
    fun addResourceItem(identifier: String, resourceData: ByteArray, applicationIdentifier: String)

    /**
     * @return the current activity.
     */
    fun getCurrentActivity(): Activity?

    /**
     * Sets the resource queue.
     */
    fun setResourceQueue(resourceQueue: SessionReplayInternalResourceQueue)
}
