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
 * Holds the sampling rates the console last sent for this application.
 *
 * They live on disk rather than in memory so a rate fetched during one launch already applies to
 * the first session of the next one, instead of every cold start beginning on the rates the app was
 * built with and only correcting itself once a request comes back.
 *
 * A rate the console did not send is absent here, never zero: the caller falls back to the value
 * passed to the SDK at init. Inventing a zero would silently stop collection nobody asked to stop.
 */
internal class RemoteSamplingStore(
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

    fun sessionReplaySampleRate(): Float? = read(replayKey())

    /**
     * Replaces what is stored with what the response carried. Rates the response omitted are
     * removed rather than left behind, so switching a knob off in the console really does hand that
     * knob back to the value the app was initialised with.
     */
    fun store(rates: RemoteSamplingRates) {
        val editor = preferences?.edit() ?: return
        write(editor, sessionKey(), rates.sessionSampleRate)
        write(editor, replayKey(), rates.sessionReplaySampleRate)
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

    companion object {
        private const val PREFERENCES_NAME = "flashcat-rum-remote-sampling"

        // SharedPreferences has no "absent" for a primitive read, and every legitimate rate is
        // within 0..100, so a negative sentinel can never collide with a stored value.
        private const val ABSENT = -1f

        internal const val STORAGE_UNAVAILABLE_MESSAGE =
            "Unable to open the remote sampling store; sampling will use the rates passed to init."

        /**
         * Identifies whose rates these are. It covers everything that can change the answer — which
         * application, in which environment, at which version — so an app that ships a new version
         * does not read the previous one's rates.
         *
         * It deliberately leaves out the SDK version: including it would discard the stored rates on
         * every SDK upgrade and put the first session after an upgrade back on the init values.
         */
        fun buildStoreKey(context: DatadogContext): String =
            "${context.service}|${context.env}|${context.version}"
    }
}

/**
 * The rates carried by one configuration response. Null means the console did not set that knob.
 */
internal data class RemoteSamplingRates(
    val sessionSampleRate: Float?,
    val sessionReplaySampleRate: Float?
) {
    fun isEmpty(): Boolean = sessionSampleRate == null && sessionReplaySampleRate == null
}
