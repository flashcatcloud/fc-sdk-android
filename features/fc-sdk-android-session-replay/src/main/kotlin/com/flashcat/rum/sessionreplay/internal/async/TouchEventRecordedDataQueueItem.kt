/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sessionreplay.internal.async

import com.flashcat.rum.sessionreplay.internal.processor.RecordedQueuedItemContext
import com.flashcat.rum.sessionreplay.model.MobileSegment

internal class TouchEventRecordedDataQueueItem(
    recordedQueuedItemContext: RecordedQueuedItemContext,
    internal val touchData: List<MobileSegment.MobileRecord> = emptyList()
) : RecordedDataQueueItem(recordedQueuedItemContext) {

    override fun isValid(): Boolean {
        return touchData.isNotEmpty()
    }

    override fun isReady(): Boolean {
        return true
    }
}
