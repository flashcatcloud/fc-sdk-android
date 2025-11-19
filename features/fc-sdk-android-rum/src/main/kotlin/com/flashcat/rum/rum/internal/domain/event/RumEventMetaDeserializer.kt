/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.rum.internal.domain.event

import com.flashcat.rum.api.InternalLogger
import com.flashcat.rum.core.internal.persistence.Deserializer
import com.google.gson.JsonParseException

internal class RumEventMetaDeserializer(
    private val internalLogger: InternalLogger
) : Deserializer<ByteArray, RumEventMeta> {
    override fun deserialize(model: ByteArray): RumEventMeta? {
        if (model.isEmpty()) return null

        return try {
            RumEventMeta.fromJson(String(model, Charsets.UTF_8), internalLogger)
        } catch (e: JsonParseException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { DESERIALIZATION_ERROR },
                e
            )
            null
        }
    }

    companion object {
        const val DESERIALIZATION_ERROR = "Failed to deserialize RUM event meta"
    }
}
