/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sessionreplay.internal.async

import com.flashcat.rum.sessionreplay.internal.processor.RecordedQueuedItemContext

internal class ResourceRecordedDataQueueItem(
    recordedQueuedItemContext: RecordedQueuedItemContext,
    val identifier: String,
    val resourceData: ByteArray,
    val mimeType: String? = null
) : RecordedDataQueueItem(recordedQueuedItemContext) {

    override fun isValid(): Boolean {
        return resourceData.isNotEmpty()
    }

    override fun isReady(): Boolean {
        return true
    }
}
