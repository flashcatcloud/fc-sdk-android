/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.core.internal.data.upload

import com.flashcat.rum.api.context.FlashcatContext
import com.flashcat.rum.api.storage.RawBatchEvent
import com.flashcat.rum.core.internal.persistence.BatchId

internal interface DataUploader {
    fun upload(
        context: FlashcatContext,
        batch: List<RawBatchEvent>,
        batchMeta: ByteArray?,
        batchId: BatchId? = null
    ): UploadStatus
}
