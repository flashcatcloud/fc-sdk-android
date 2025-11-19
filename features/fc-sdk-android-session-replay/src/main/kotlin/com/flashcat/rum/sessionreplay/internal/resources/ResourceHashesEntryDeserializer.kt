/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sessionreplay.internal.resources

import com.flashcat.rum.api.InternalLogger
import com.flashcat.rum.core.internal.persistence.Deserializer
import com.flashcat.rum.sessionreplay.model.ResourceHashesEntry
import com.google.gson.JsonParseException
import java.util.Locale

internal class ResourceHashesEntryDeserializer(
    private val internalLogger: InternalLogger
) : Deserializer<String, ResourceHashesEntry> {
    override fun deserialize(model: String): ResourceHashesEntry? {
        return try {
            ResourceHashesEntry.fromJson(model)
        } catch (e: JsonParseException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                {
                    DESERIALIZE_ERROR_MESSAGE_FORMAT.format(
                        Locale.US,
                        model
                    )
                }
            )
            null
        }
    }

    internal companion object {
        internal const val DESERIALIZE_ERROR_MESSAGE_FORMAT =
            "Error while trying to deserialize the ResourceHashesEntry: %s"
    }
}
