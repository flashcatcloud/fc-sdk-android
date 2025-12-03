/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.log.internal.storage

import androidx.annotation.WorkerThread
import cloud.flashcat.android.api.InternalLogger
import cloud.flashcat.android.api.storage.DataWriter
import cloud.flashcat.android.api.storage.EventBatchWriter
import cloud.flashcat.android.api.storage.EventType
import cloud.flashcat.android.api.storage.RawBatchEvent
import cloud.flashcat.android.core.persistence.Serializer
import cloud.flashcat.android.core.persistence.serializeToByteArray
import cloud.flashcat.android.log.model.LogEvent

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
