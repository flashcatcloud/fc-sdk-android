/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.remoteconfig

import org.json.JSONException
import org.json.JSONObject

/**
 * FLASHCAT FORK - the configuration a session was drawn under: the rates actually used at the draw
 * (the console's where it set them, the init values where it did not) and the remote settings
 * version they came from. Events carry these instead of the init values, so server-side
 * extrapolation and audits line up with the draw that kept the session — a session is never
 * re-judged, so the metadata must be from its creation, not from whatever has arrived since.
 */
internal data class DrawnConfiguration(
    /** The session this record belongs to; a record naming another session is stale and inert. */
    val sessionId: String,
    /** The remote settings version the draw read, or 0 when none was ever fetched. */
    val version: Int,
    val sessionSampleRate: Float,
    /** Null when the draw could not know it (Session Replay not publishing); then not reported. */
    val sessionReplaySampleRate: Float?
) {

    fun toJsonString(): String = JSONObject()
        .put(FIELD_SESSION_ID, sessionId)
        .put(FIELD_VERSION, version)
        .put(FIELD_SESSION_SAMPLE_RATE, sessionSampleRate.toDouble())
        .apply {
            if (sessionReplaySampleRate != null) {
                put(FIELD_SESSION_REPLAY_SAMPLE_RATE, sessionReplaySampleRate.toDouble())
            }
        }
        .toString()

    companion object {
        private const val FIELD_SESSION_ID = "id"
        private const val FIELD_VERSION = "version"
        private const val FIELD_SESSION_SAMPLE_RATE = "sessionSampleRate"
        private const val FIELD_SESSION_REPLAY_SAMPLE_RATE = "sessionReplaySampleRate"

        /**
         * Parses a stored record, tolerating what older versions did not write: a field missing
         * from an old record reads as if the console never set that knob, so an SDK upgrade
         * changes nothing for a session already drawn.
         */
        fun fromJsonString(json: String): DrawnConfiguration? = try {
            val obj = JSONObject(json)
            val sessionId = obj.optString(FIELD_SESSION_ID).takeIf { it.isNotEmpty() }
            if (sessionId == null || !obj.has(FIELD_SESSION_SAMPLE_RATE)) {
                null
            } else {
                DrawnConfiguration(
                    sessionId = sessionId,
                    version = obj.optInt(FIELD_VERSION, 0),
                    sessionSampleRate = obj.getDouble(FIELD_SESSION_SAMPLE_RATE).toFloat(),
                    sessionReplaySampleRate = if (obj.has(FIELD_SESSION_REPLAY_SAMPLE_RATE)) {
                        obj.getDouble(FIELD_SESSION_REPLAY_SAMPLE_RATE).toFloat()
                    } else {
                        null
                    }
                )
            }
        } catch (e: JSONException) {
            // Storage holding something we did not write is no record at all.
            null
        }
    }
}
