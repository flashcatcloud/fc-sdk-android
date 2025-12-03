/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.webview.internal.storage

import androidx.annotation.WorkerThread
import cloud.flashcat.android.api.InternalLogger
import cloud.flashcat.android.api.storage.DataWriter
import cloud.flashcat.android.api.storage.EventBatchWriter
import cloud.flashcat.android.api.storage.EventType
import cloud.flashcat.android.api.storage.RawBatchEvent
import cloud.flashcat.android.core.persistence.Serializer
import cloud.flashcat.android.core.persistence.serializeToByteArray
import com.google.gson.JsonObject

internal class WebViewDataWriter(
    private val serializer: Serializer<JsonObject>,
    private val internalLogger: InternalLogger
) : DataWriter<JsonObject> {

    @WorkerThread
    override fun write(writer: EventBatchWriter, element: JsonObject, eventType: EventType): Boolean {
        // TODO RUM-374 If event is RUM ViewEvent (as Json), we need to store it as last view
        //  event for more precise NDK crash reporting
        val serialized = serializer.serializeToByteArray(element, internalLogger) ?: return false
        return synchronized(this) {
            writer.write(
                event = RawBatchEvent(data = serialized),
                batchMetadata = null,
                eventType = eventType
            )
        }
    }
}
