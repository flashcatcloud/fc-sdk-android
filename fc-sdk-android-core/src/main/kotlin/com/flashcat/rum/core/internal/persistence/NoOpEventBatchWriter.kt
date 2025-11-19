/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.core.internal.persistence

import com.flashcat.rum.api.storage.EventBatchWriter
import com.flashcat.rum.api.storage.EventType
import com.flashcat.rum.api.storage.RawBatchEvent

internal class NoOpEventBatchWriter : EventBatchWriter {

    override fun currentMetadata(): ByteArray? {
        return null
    }

    override fun write(
        event: RawBatchEvent,
        batchMetadata: ByteArray?,
        eventType: EventType
    ): Boolean = true
}
