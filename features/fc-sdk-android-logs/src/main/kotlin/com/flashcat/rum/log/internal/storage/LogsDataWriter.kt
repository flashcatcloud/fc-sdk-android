/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.log.internal.storage

import androidx.annotation.WorkerThread
import com.flashcat.rum.api.InternalLogger
import com.flashcat.rum.api.storage.DataWriter
import com.flashcat.rum.api.storage.EventBatchWriter
import com.flashcat.rum.api.storage.EventType
import com.flashcat.rum.api.storage.RawBatchEvent
import com.flashcat.rum.core.persistence.Serializer
import com.flashcat.rum.core.persistence.serializeToByteArray
import com.flashcat.rum.log.model.LogEvent

internal class LogsDataWriter(
    internal val serializer: Serializer<LogEvent>,
    private val internalLogger: InternalLogger
) : DataWriter<LogEvent> {

    @WorkerThread
    override fun write(writer: EventBatchWriter, element: LogEvent, eventType: EventType): Boolean {
        val serialized = serializer.serializeToByteArray(element, internalLogger) ?: return false
        return synchronized(this) {
            writer.write(RawBatchEvent(data = serialized), batchMetadata = null, eventType = eventType)
        }
    }
}
