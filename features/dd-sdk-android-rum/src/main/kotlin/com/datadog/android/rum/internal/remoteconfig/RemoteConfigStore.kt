/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.remoteconfig

import android.content.Context
import android.content.SharedPreferences
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext

/**
 * Holds the remote configuration the console last sent for this application.
 *
 * They live on disk rather than in memory so a rate fetched during one launch already applies to
 * the first session of the next one, instead of every cold start beginning on the rates the app was
 * built with and only correcting itself once a request comes back.
 *
 * A rate the console did not send is absent here, never zero: the caller falls back to the value
 * passed to the SDK at init. Inventing a zero would silently stop collection nobody asked to stop.
 */
internal class RemoteConfigStore(
    appContext: Context,
    private val storeKey: String,
    private val internalLogger: InternalLogger
) {

    private val preferences: SharedPreferences? = try {
        appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    } catch (e: SecurityException) {
        internalLogger.log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            { STORAGE_UNAVAILABLE_MESSAGE },
            e
        )
        null
    }

    fun sessionSampleRate(): Float? = read(sessionKey())

    /**
     * The application-defined bag the console last published, as the raw JSON object string, or
     * null when none is published. The platform never interprets it — see [RumMonitor.getRemoteConfig].
     */
    fun custom(): String? = preferences?.getString(customKey(), null)

    fun sessionReplaySampleRate(): Float? = read(replayKey())

    /**
     * The validator the server sent with the stored configuration, echoed back as If-None-Match so
     * an unchanged answer costs a 304 instead of a body. It belongs to this stored configuration
     * specifically: the answer varies per caller, so it cannot be shared or guessed.
     */
    fun etag(): String? = preferences?.getString(etagKey(), null)

    /**
     * Which configuration the given session was drawn under, kept next to the values it was drawn
     * from. The session id inside is the validity check: a record from a previous, expired session
     * simply never matches again.
     */
    fun storeDrawRecord(record: DrawnConfiguration) {
        preferences?.edit()?.putString(drawRecordKey(), record.toJsonString())?.apply()
    }

    fun readDrawRecord(): DrawnConfiguration? =
        preferences?.getString(drawRecordKey(), null)?.let { DrawnConfiguration.fromJsonString(it) }

    /**
     * Which version of the settings the stored rates came from, or null before the first answer.
     * Reported back on the next request so the console can say how far a change has reached — a
     * question the events cannot answer, because a session that was not kept sends none, and the
     * miss rate is set by the very rate being changed.
     */
    fun appliedVersion(): Int? {
        val stored = preferences?.getInt(versionKey(), ABSENT_VERSION) ?: ABSENT_VERSION
        return if (stored == ABSENT_VERSION) null else stored
    }

    /**
     * Replaces what is stored with what the response carried. Rates the response omitted are
     * removed rather than left behind, so switching a knob off in the console really does hand that
     * knob back to the value the app was initialised with.
     */
    fun store(values: RemoteConfigValues) {
        val editor = preferences?.edit() ?: return
        write(editor, sessionKey(), values.sessionSampleRate)
        write(editor, replayKey(), values.sessionReplaySampleRate)
        // Kept even when there are no rates — that is what "remote configuration is off, use your
        // own settings" looks like — so the console can still see this client is up to date with
        // the change that turned them off.
        if (values.version == null) {
            editor.remove(versionKey())
        } else {
            editor.putInt(versionKey(), values.version)
        }
        if (values.custom == null) {
            editor.remove(customKey())
        } else {
            editor.putString(customKey(), values.custom)
        }
        if (values.etag == null) {
            editor.remove(etagKey())
        } else {
            editor.putString(etagKey(), values.etag)
        }
        editor.apply()
    }

    private fun read(key: String): Float? {
        val stored = preferences?.getFloat(key, ABSENT) ?: ABSENT
        return if (stored == ABSENT) null else stored
    }

    private fun write(editor: SharedPreferences.Editor, key: String, rate: Float?) {
        if (rate == null) {
            editor.remove(key)
        } else {
            editor.putFloat(key, rate)
        }
    }

    private fun sessionKey() = "$storeKey.sessionSampleRate"

    private fun replayKey() = "$storeKey.sessionReplaySampleRate"

    private fun versionKey() = "$storeKey.version"

    private fun customKey() = "$storeKey.custom"

    private fun etagKey() = "$storeKey.etag"

    private fun drawRecordKey() = "$storeKey.draw"

    companion object {
        private const val PREFERENCES_NAME = "flashcat-rum-remote-config"

        /**
         * The `1` is the storage format version, not the SDK version: it changes only when the
         * shape of what we store changes, so an SDK upgrade keeps the cache (losing it would put
         * the first session after every upgrade back on the init values), while a format change
         * orphans the old entry instead of asking new code to parse it.
         */
        internal const val STORE_KEY_PREFIX = "_fc_rc_1_"

        // SharedPreferences has no "absent" for a primitive read, and every legitimate rate is
        // within 0..100, so a negative sentinel can never collide with a stored value.
        private const val ABSENT = -1f
        private const val ABSENT_VERSION = -1

        internal const val STORAGE_UNAVAILABLE_MESSAGE =
            "Unable to open the remote configuration store; the values passed to init will apply."

        /**
         * Identifies whose configuration this is. It covers everything that can change the answer —
         * which endpoint the app asks, which application, in which environment, at which app
         * version — so an app that ships a new version, or two applications sharing a device, never
         * read each other's values.
         *
         * It deliberately leaves out the SDK version: including it would discard the stored values
         * on every SDK upgrade and put the first session after an upgrade back on the init values.
         * The storage format version lives in [STORE_KEY_PREFIX] instead, so only a real format
         * change orphans the cache.
         */
        fun buildStoreKey(context: DatadogContext, intakeUrl: String, applicationId: String): String {
            val host = try {
                @Suppress("UnsafeThirdPartyFunctionCall") // caught right below
                java.net.URI(intakeUrl).host ?: intakeUrl
            } catch (e: IllegalArgumentException) {
                intakeUrl
            } catch (e: java.net.URISyntaxException) {
                intakeUrl
            }
            return STORE_KEY_PREFIX + listOf(
                host,
                applicationId,
                context.service,
                context.env,
                context.version
            ).joinToString("|")
        }
    }
}

/**
 * The values carried by one configuration response. Null means the console did not set that knob.
 */
internal data class RemoteConfigValues(
    val sessionSampleRate: Float?,
    val sessionReplaySampleRate: Float?,
    val version: Int? = null,
    /** Raw JSON object string of the console's custom pass-through values, delivered verbatim. */
    val custom: String? = null,
    /** The validator to echo back as If-None-Match on the next request, quoted as the server sent it. */
    val etag: String? = null
) {
    fun isEmpty(): Boolean = sessionSampleRate == null && sessionReplaySampleRate == null
}
