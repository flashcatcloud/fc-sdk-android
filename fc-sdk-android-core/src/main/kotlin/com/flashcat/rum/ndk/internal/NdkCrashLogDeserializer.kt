/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.ndk.internal

import com.flashcat.rum.api.InternalLogger
import com.flashcat.rum.core.internal.persistence.Deserializer
import com.google.gson.JsonParseException
import java.util.Locale

internal class NdkCrashLogDeserializer(
    private val internalLogger: InternalLogger
) : Deserializer<String, NdkCrashLog> {

    override fun deserialize(model: String): NdkCrashLog? {
        return try {
            NdkCrashLog.fromJson(model)
        } catch (e: JsonParseException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { DESERIALIZE_ERROR_MESSAGE_FORMAT.format(Locale.US, model) },
                e
            )
            null
        } catch (e: IllegalStateException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { DESERIALIZE_ERROR_MESSAGE_FORMAT.format(Locale.US, model) },
                e
            )
            null
        }
    }

    companion object {
        const val DESERIALIZE_ERROR_MESSAGE_FORMAT =
            "Error while trying to deserialize the NDK Crash info: %s"
    }
}
