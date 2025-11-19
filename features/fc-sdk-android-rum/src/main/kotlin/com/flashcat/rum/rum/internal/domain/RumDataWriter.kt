/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.rum.internal.domain

import androidx.annotation.WorkerThread
import com.flashcat.rum.api.storage.DataWriter
import com.flashcat.rum.api.storage.EventBatchWriter
import com.flashcat.rum.api.storage.EventType
import com.flashcat.rum.api.storage.RawBatchEvent
import com.flashcat.rum.core.InternalSdkCore
import com.flashcat.rum.core.persistence.Serializer
import com.flashcat.rum.core.persistence.serializeToByteArray
import com.flashcat.rum.rum.internal.domain.event.RumEventMeta
import com.flashcat.rum.rum.model.ViewEvent

internal class RumDataWriter(
    internal val eventSerializer: Serializer<Any>,
    private val eventMetaSerializer: Serializer<RumEventMeta>,
    private val sdkCore: InternalSdkCore
) : DataWriter<Any> {

    // region DataWriter

    @WorkerThread
    override fun write(writer: EventBatchWriter, element: Any, eventType: EventType): Boolean {
        val byteArray = eventSerializer.serializeToByteArray(
            element,
            sdkCore.internalLogger
        ) ?: return false

        val batchEvent = if (element is ViewEvent) {
            val hasAccessibility = element.view.accessibility != null

            val eventMeta = RumEventMeta.View(
                viewId = element.view.id,
                documentVersion = element.dd.documentVersion,
                hasAccessibility = hasAccessibility
            )
            val serializedEventMeta =
                eventMetaSerializer.serializeToByteArray(eventMeta, sdkCore.internalLogger)
                    ?: EMPTY_BYTE_ARRAY
            RawBatchEvent(
                data = byteArray,
                metadata = serializedEventMeta
            )
        } else {
            RawBatchEvent(data = byteArray)
        }

        synchronized(this) {
            val result = writer.write(batchEvent, null, eventType)
            if (result) {
                onDataWritten(element, byteArray)
            }
            return result
        }
    }

    // endregion

    // region Internal

    @WorkerThread
    internal fun onDataWritten(data: Any, rawData: ByteArray) {
        when (data) {
            is ViewEvent -> sdkCore.writeLastViewEvent(rawData)
        }
    }

    // endregion

    companion object {
        val EMPTY_BYTE_ARRAY = ByteArray(0)
    }
}
