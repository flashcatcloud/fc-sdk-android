/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.core.internal.persistence

import androidx.annotation.WorkerThread
import com.flashcat.rum.api.storage.RawBatchEvent

internal interface BatchReader {

    /**
     * @return the metadata of the current readable file
     */
    @WorkerThread
    fun currentMetadata(): ByteArray?

    @WorkerThread
    fun read(): List<RawBatchEvent>
}
