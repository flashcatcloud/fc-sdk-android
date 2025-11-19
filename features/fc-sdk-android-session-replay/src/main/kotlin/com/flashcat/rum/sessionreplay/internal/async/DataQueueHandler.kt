/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sessionreplay.internal.async

import com.flashcat.rum.sessionreplay.model.MobileSegment
import com.flashcat.rum.sessionreplay.recorder.SystemInformation

internal interface DataQueueHandler {
    fun addResourceItem(
        identifier: String,
        resourceData: ByteArray,
        mimeType: String? = null
    ): ResourceRecordedDataQueueItem?
    fun addTouchEventItem(
        pointerInteractions: List<MobileSegment.MobileRecord>
    ): TouchEventRecordedDataQueueItem?
    fun addSnapshotItem(systemInformation: SystemInformation): SnapshotRecordedDataQueueItem?
    fun tryToConsumeItems()
    fun clearAndStopProcessingQueue()
}
