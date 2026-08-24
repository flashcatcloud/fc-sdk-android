/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.remoteconfig

import android.os.SystemClock
import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import okhttp3.Call
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Keeps the stored remote configuration in step with what the console says.
 *
 * Nothing here can hold up the SDK or interrupt collection: the first fetch is scheduled like any
 * other, and a request that fails, times out or comes back unreadable leaves the stored rates
 * exactly as they were. Wiping them on a bad minute would swing a whole fleet back to the rates it
 * was built with, which is the opposite of what someone who turned a knob deliberately wants.
 */
internal class RemoteConfigController(
    private val sdkCore: FeatureSdkCore,
    private val configUrl: String,
    private val store: RemoteConfigStore,
    private val initialSessionSampleRate: Float,
    private val callFactory: Call.Factory,
    private val executor: ScheduledExecutorService,
    private val restartSession: () -> Unit,
    private val elapsedTimeMs: () -> Long = SystemClock::elapsedRealtime
) {

    @Volatile
    private var lastFetchAtMs: Long = 0

    @Volatile
    private var currentTtlSeconds: Long = DEFAULT_TTL_SECONDS

    @Volatile
    private var refreshOnForeground: Boolean = false

    fun start() {
        schedule(0L)
    }

    /**
     * Asks again when the app returns to the foreground, where the poll timer cannot be trusted:
     * the system may not have run it for hours.
     *
     * Off unless an operator turned it on for this application. The poll spreads requests across
     * the ttl; returning to the foreground does the opposite, bunching them at the moment everyone
     * opens the app — the same shape as a release herd, arriving when the endpoint can least
     * absorb it. Worth it for an application whose owner needs a change to land within minutes,
     * not worth it for everyone else, so it is theirs to choose rather than ours to assume.
     *
     * The staleness check is the second guard: it keeps switching between apps from turning into a
     * request each time.
     */
    fun refreshIfStale() {
        if (shouldRefreshOnForeground(refreshOnForeground, elapsedTimeMs() - lastFetchAtMs, currentTtlSeconds)) {
            schedule(0L)
        }
    }

    fun stop() {
        executor.shutdownNow()
    }

    private fun schedule(delaySeconds: Long) {
        try {
            executor.schedule({ fetchOnce() }, delaySeconds, TimeUnit.SECONDS)
        } catch (e: RejectedExecutionException) {
            // The SDK is shutting down. Nothing to keep fresh.
            sdkCore.internalLogger.log(
                InternalLogger.Level.DEBUG,
                InternalLogger.Target.MAINTAINER,
                { "Remote configuration refresh not scheduled: executor is shutting down." },
                e
            )
        }
    }

    @WorkerThread
    private fun fetchOnce() {
        // Armed before the request goes out, so a request that never comes back still leads to
        // another attempt instead of leaving the app on whatever it last knew, forever.
        var nextDelaySeconds = DEFAULT_TTL_SECONDS
        lastFetchAtMs = elapsedTimeMs()

        try {
            // Telling the server which version this app is running is what lets the console answer
            // "has my change reached everyone yet". It goes on the request every client makes,
            // whether or not its session was kept.
            val url = store.appliedVersion()?.let { "$configUrl&applied_version=$it" } ?: configUrl
            val request = Request.Builder().url(url).get().build()
            callFactory.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val payload = response.body?.string()
                    if (payload != null) {
                        nextDelaySeconds = apply(payload)
                    }
                }
            }
        } catch (e: IOException) {
            logFetchFailure(e)
        } catch (e: IllegalStateException) {
            logFetchFailure(e)
        }

        schedule(nextDelaySeconds)
    }

    /**
     * Stores what the response carried and, when the console asked for it, restarts the session so
     * the new rates take hold now instead of at the visitor's next one.
     *
     * The session is only restarted when the rates this client will draw with really changed.
     * Without that check, a console resending an unchanged configuration would cut every session in
     * two on every poll.
     */
    internal fun apply(payload: String): Long {
        val json = JSONObject(payload)
        val ttl = json.optLong(FIELD_TTL, DEFAULT_TTL_SECONDS)
        val enabled = json.optBoolean(FIELD_ENABLED, false)
        val activation = json.optString(FIELD_ACTIVATION, ACTIVATION_NEXT_SESSION)
        refreshOnForeground = json.optBoolean(FIELD_REFRESH_ON_FOREGROUND, false)

        val before = RemoteConfigValues(store.sessionSampleRate(), store.sessionReplaySampleRate())
        val version = json.optInt(FIELD_VERSION, 0).takeIf { it > 0 }
        val after = if (enabled) {
            readRates(json.optJSONObject(FIELD_RUM)).copy(
                version = version,
                // Stored as the raw string: the platform's job is delivery, the meaning belongs to
                // the host application.
                custom = json.optJSONObject(FIELD_CUSTOM)?.toString()
            )
        } else {
            EMPTY_RATES.copy(version = version)
        }
        store.store(after)

        if (activation == ACTIVATION_IMMEDIATE && changesThisClient(before, after)) {
            restartSession()
        }

        // Remembered here rather than around the request, so a fetch that fails keeps the ttl the
        // server last asked for instead of falling back to ours.
        currentTtlSeconds = if (ttl > 0) ttl else DEFAULT_TTL_SECONDS
        return currentTtlSeconds
    }

    private fun readRates(rum: JSONObject?): RemoteConfigValues {
        if (rum == null) return EMPTY_RATES
        return RemoteConfigValues(
            sessionSampleRate = readRate(rum, FIELD_SESSION_SAMPLE_RATE),
            sessionReplaySampleRate = readRate(rum, FIELD_SESSION_REPLAY_SAMPLE_RATE)
        )
    }

    /**
     * A rate the response did not send stays absent, so the value passed to init keeps applying.
     * An out-of-range number is treated the same way rather than clamped: a rate we cannot trust is
     * not a rate to sample a customer's traffic with.
     */
    private fun readRate(rum: JSONObject, field: String): Float? {
        if (!rum.has(field)) return null
        val rate = rum.optDouble(field, Double.NaN)
        return if (rate.isNaN() || rate < 0.0 || rate > MAX_RATE) null else rate.toFloat()
    }

    private fun changesThisClient(before: RemoteConfigValues, after: RemoteConfigValues): Boolean {
        val sessionBefore = before.sessionSampleRate ?: initialSessionSampleRate
        val sessionAfter = after.sessionSampleRate ?: initialSessionSampleRate

        // The replay rate is configured on the Session Replay feature rather than here, so there is
        // no init value to fall back to on this side. Comparing what was stored is exact for every
        // change after the first, and at worst restarts one session the first time the console sets
        // a replay rate that happens to equal the one the app was built with.
        return sessionBefore != sessionAfter ||
            before.sessionReplaySampleRate != after.sessionReplaySampleRate
    }

    private fun logFetchFailure(e: Throwable) {
        sdkCore.internalLogger.log(
            InternalLogger.Level.DEBUG,
            InternalLogger.Target.MAINTAINER,
            { FETCH_FAILED_MESSAGE },
            e
        )
    }

    companion object {
        internal const val DEFAULT_TTL_SECONDS = 300L
        internal const val ACTIVATION_NEXT_SESSION = "next_session"
        internal const val ACTIVATION_IMMEDIATE = "immediate"

        private const val MAX_RATE = 100.0
        private const val MILLIS_PER_SECOND = 1_000L
        private const val FIELD_VERSION = "version"
        private const val FIELD_REFRESH_ON_FOREGROUND = "refresh_on_foreground"
        private const val FIELD_TTL = "ttl"
        private const val FIELD_ENABLED = "enabled"
        private const val FIELD_ACTIVATION = "activation"
        private const val FIELD_CUSTOM = "custom"
        private const val FIELD_RUM = "rum"
        private const val FIELD_SESSION_SAMPLE_RATE = "sessionSampleRate"
        private const val FIELD_SESSION_REPLAY_SAMPLE_RATE = "sessionReplaySampleRate"

        private val EMPTY_RATES = RemoteConfigValues(null, null)

        internal const val FETCH_FAILED_MESSAGE =
            "Unable to refresh the remote configuration; keeping the values already in use."

        /**
         * Where to ask. A custom endpoint means the app was pointed at the customer's own host for
         * the RUM intake, and the configuration lives beside it there — which is exactly the layout
         * the private-deployment nginx template serves.
         */
        fun buildConfigUrl(intakeUrl: String, clientToken: String, env: String, appVersion: String): String {
            val parameters = buildString {
                append("?client_token=").append(encode(clientToken))
                append("&sdk=android")
                if (env.isNotEmpty()) append("&env=").append(encode(env))
                if (appVersion.isNotEmpty()) append("&app_version=").append(encode(appVersion))
            }
            return intakeUrl.trimEnd('/') + "/config" + parameters
        }

        private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

        /**
         * Whether returning to the foreground is a reason to ask again.
         *
         * Both halves guard different things: the permission keeps the request pattern off unless
         * someone chose it, and the age keeps app switching from becoming a request each time.
         */
        internal fun shouldRefreshOnForeground(allowed: Boolean, ageMs: Long, ttlSeconds: Long): Boolean =
            allowed && ageMs >= ttlSeconds * MILLIS_PER_SECOND
    }
}
