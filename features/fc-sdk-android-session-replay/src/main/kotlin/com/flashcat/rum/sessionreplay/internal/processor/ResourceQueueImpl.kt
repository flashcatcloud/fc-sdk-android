/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sessionreplay.internal.processor

import androidx.annotation.MainThread
import com.flashcat.rum.sessionreplay.SessionReplayInternalResourceQueue
import com.flashcat.rum.sessionreplay.internal.async.RecordedDataQueueHandler

internal class ResourceQueueImpl(
    private val internalHandler: RecordedDataQueueHandler
) : SessionReplayInternalResourceQueue {
    @MainThread
    override fun addResourceItem(identifier: String, resourceData: ByteArray, mimeType: String?) {
        internalHandler.addResourceItem(identifier, resourceData, mimeType)
    }
}
