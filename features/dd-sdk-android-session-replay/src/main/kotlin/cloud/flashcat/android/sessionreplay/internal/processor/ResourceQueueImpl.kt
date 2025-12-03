/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.sessionreplay.internal.processor

import androidx.annotation.MainThread
import cloud.flashcat.android.sessionreplay.SessionReplayInternalResourceQueue
import cloud.flashcat.android.sessionreplay.internal.async.RecordedDataQueueHandler

internal class ResourceQueueImpl(
    private val internalHandler: RecordedDataQueueHandler
) : SessionReplayInternalResourceQueue {
    @MainThread
    override fun addResourceItem(identifier: String, resourceData: ByteArray, mimeType: String?) {
        internalHandler.addResourceItem(identifier, resourceData, mimeType)
    }
}
