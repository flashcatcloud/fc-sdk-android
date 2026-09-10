/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.remoteconfig

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * FLASHCAT FORK - decodes the console's custom bag into plain Kotlin values.
 *
 * Stored as the raw JSON the console sent, because storage has no reason to understand it, but
 * handed to the host application decoded: every other platform hands back a dictionary, and
 * leaving one of them to parse a string would make the same console value cost more on Android
 * than anywhere else.
 *
 * A body we cannot parse reads as nothing published rather than as an error: the bag is
 * application-defined, and no rate or decision depends on it.
 */
internal fun decodeCustomValues(json: String?): Map<String, Any?>? {
    if (json == null) return null
    return try {
        JSONObject(json).asMap()
    } catch (e: JSONException) {
        null
    }
}

private fun JSONObject.asMap(): Map<String, Any?> =
    keys().asSequence().associateWith { unwrap(get(it)) }

private fun JSONArray.asList(): List<Any?> =
    (0 until length()).map { unwrap(get(it)) }

private fun unwrap(value: Any?): Any? = when (value) {
    JSONObject.NULL -> null
    is JSONObject -> value.asMap()
    is JSONArray -> value.asList()
    else -> value
}
