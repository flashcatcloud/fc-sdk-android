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
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

/**
 * Keeps the stored remote configuration in step with what the console says.
 *
 * Fetching follows the rhythm of the sessions that read it: once at start-up and once whenever a
 * new session begins — a change can only matter at the next draw, so asking more often than
 * sessions are drawn would be requests for nothing. There is no timer between sessions; the
 * server's `ttl` field is accepted and only bounds how stale the stored values may be when the
 * console allows a foreground refresh, reserved for a future polling mode.
 *
 * Nothing here can hold up the SDK or interrupt collection: a trigger never blocks on the request,
 * and a request that fails, times out or comes back unreadable leaves the stored values exactly
 * as they were. Wiping them on a bad minute would swing a whole fleet back to the values it was
 * built with, which is the opposite of what someone who turned a knob deliberately wants.
 */
internal class RemoteConfigController(
    private val sdkCore: FeatureSdkCore,
    private val configUrl: String,
    private val store: RemoteConfigStore,
    private val initialSessionSampleRate: Float,
    private val callFactory: Call.Factory,
    private val executor: ScheduledExecutorService,
    private val restartSession: () -> Unit,
    private val elapsedTimeMs: () -> Long = SystemClock::elapsedRealtime,
    private val jitter: () -> Double = { Random.nextDouble() }
) {

    @Volatile
    private var lastFetchAtMs: Long = 0

    @Volatile
    private var currentTtlSeconds: Long = DEFAULT_TTL_SECONDS

    @Volatile
    private var refreshOnForeground: Boolean = false

    private val inFlight = AtomicBoolean(false)

    /** Whether the entries of app versions this device no longer runs have been cleared yet. */
    private val swept = AtomicBoolean(false)
    private var failedAttempts = 0
    private var pendingRetry: ScheduledFuture<*>? = null

    fun start() = triggerFetch()

    /**
     * A new session is the one moment a changed configuration can matter: its draw has just
     * happened with whatever was stored, and the response to this request lands in storage for
     * the next draw. It never waits for the request — a session is never delayed by the network.
     */
    fun onSessionStarted() = triggerFetch()

    /**
     * Asks again when the app returns to the foreground, where timers cannot be trusted:
     * the system may not have run them for hours.
     *
     * Off unless an operator turned it on for this application. Session starts spread requests
     * across the day; returning to the foreground does the opposite, bunching them at the moment
     * everyone opens the app — the same shape as a release herd, arriving when the endpoint can
     * least absorb it. Worth it for an application whose owner needs a change to land within
     * minutes, not worth it for everyone else, so it is theirs to choose rather than ours to
     * assume.
     *
     * The staleness check is the second guard: it keeps switching between apps from turning into a
     * request each time.
     */
    fun refreshIfStale() {
        if (shouldRefreshOnForeground(refreshOnForeground, elapsedTimeMs() - lastFetchAtMs, currentTtlSeconds)) {
            triggerFetch()
        }
    }

    fun stop() {
        executor.shutdownNow()
    }

    /**
     * Runs a fetch now, dropping any retry still waiting: a natural trigger re-arms the whole
     * backoff, so a session starting in the middle of an outage does not wait out the patient
     * retry before asking again.
     */
    private fun triggerFetch() {
        synchronized(this) {
            pendingRetry?.cancel(false)
            failedAttempts = 0
        }
        if (!inFlight.compareAndSet(false, true)) return
        try {
            executor.execute { fetchOnce() }
        } catch (e: RejectedExecutionException) {
            // The SDK is shutting down. Nothing to keep fresh.
            inFlight.set(false)
            logScheduleRejected(e)
        }
    }

    @WorkerThread
    private fun fetchOnce() {
        try {
            fetchAndApply()
        } finally {
            // Released whatever happened above, because every later fetch — a new session, a
            // return to the foreground, a retry — is gated on this flag. Anything that got out of
            // here without clearing it would end remote configuration for the rest of the
            // process's life, silently and with nothing left to ask again.
            inFlight.set(false)
        }
    }

    @WorkerThread
    private fun fetchAndApply() {
        // Housekeeping, once per launch and here rather than at construction: this is the first
        // place that is both off the main thread — nothing about remote configuration may hold up
        // initialisation — and certain to run before anything is stored. Repeating it at every
        // fetch would walk the preferences file again at every session start to learn nothing new.
        if (swept.compareAndSet(false, true)) {
            store.sweepAbandoned()
        }

        // Stamped before the request goes out, so a request that never comes back still counts as
        // an attempt for the staleness gate instead of leaving the app on whatever it last knew.
        lastFetchAtMs = elapsedTimeMs()

        val succeeded = try {
            // Telling the server which version this app is running is what lets the console answer
            // "has my change reached everyone yet". It goes on the request every client makes,
            // whether or not its session was kept.
            val url = store.appliedVersion()?.let { "$configUrl&applied_version=$it" } ?: configUrl
            val requestBuilder = Request.Builder().url(url).get()
            // The answer varies per caller, so the validator only means something paired with the
            // configuration it validated: it is stored beside it and echoed back exactly as sent.
            store.etag()?.let { requestBuilder.header(HEADER_IF_NONE_MATCH, it) }
            callFactory.newCall(requestBuilder.build()).execute().use { response ->
                when {
                    // Unchanged: what is stored is still the answer, so there is nothing to apply —
                    // but the ask itself succeeded, and no retry is owed. The entry is still marked
                    // as in use, because this is the one answer that stores nothing and the sweep
                    // reads nothing but age.
                    response.code == HTTP_NOT_MODIFIED -> {
                        store.touch()
                        true
                    }
                    response.isSuccessful -> {
                        val payload = response.body?.string()
                        if (payload == null) {
                            false
                        } else {
                            // An unreadable body is the only outcome worth asking again for. A
                            // body we understood — even one we must refuse because its schema is
                            // newer than this SDK — is an answered question, and repeating it
                            // would just be the same refusal twice.
                            apply(payload, response.header(HEADER_ETAG)) != Outcome.UNREADABLE
                        }
                    }
                    else -> false
                }
            }
        } catch (e: IOException) {
            logFetchFailure(e)
            false
        } catch (e: IllegalStateException) {
            logFetchFailure(e)
            false
        }

        // The flag this fetch holds is released by the caller's `finally`, not here: a retry only
        // schedules work for later, and the runnable it schedules takes the flag for itself.
        if (!succeeded) scheduleRetry()
    }

    /**
     * A failed fetch is retried quickly, then patiently, then not at all until the next natural
     * trigger (a new session, or the next app start). The budget is deliberately tiny — two extra
     * requests per outage per client, so a fleet can never turn an endpoint incident into a storm.
     */
    private fun scheduleRetry() {
        synchronized(this) {
            if (failedAttempts >= RETRY_DELAYS_SECONDS.size) return
            val delaySeconds = jittered(RETRY_DELAYS_SECONDS[failedAttempts], jitter())
            failedAttempts++
            try {
                pendingRetry = executor.schedule(
                    { if (inFlight.compareAndSet(false, true)) fetchOnce() },
                    delaySeconds,
                    TimeUnit.SECONDS
                )
            } catch (e: RejectedExecutionException) {
                // The SDK is shutting down. Nothing to keep fresh.
                logScheduleRejected(e)
            }
        }
    }

    /**
     * What reading one response body came to. Only [UNREADABLE] is worth asking again for: the
     * other two are answers, whether or not this SDK can act on them.
     */
    internal enum class Outcome {
        /** The body was read and its values are now stored. */
        APPLIED,

        /**
         * The body was not a configuration at all — not JSON, or truncated. A captive portal
         * answering 200 with a login page looks exactly like this, so it is treated as a request
         * that did not arrive rather than as a configuration saying nothing.
         */
        UNREADABLE,

        /**
         * The body is a configuration written to a contract this SDK does not know. Refused whole:
         * a payload shaped for a newer reader can be misread field by field while every individual
         * field still parses, and half-understood sampling settings are worse than none.
         */
        UNSUPPORTED_SCHEMA
    }

    /**
     * Stores what the response carried and, when the console asked for it, restarts the session so
     * the new values take hold now instead of at the visitor's next one.
     *
     * The session is only restarted when the values this client will draw with really changed.
     * Without that check, a console resending an unchanged configuration would cut every session in
     * two on every fetch.
     */
    internal fun apply(payload: String, etag: String? = null): Outcome {
        val json = try {
            @Suppress("UnsafeThirdPartyFunctionCall") // caught right here
            JSONObject(payload)
        } catch (e: JSONException) {
            logUnreadableBody(e)
            return Outcome.UNREADABLE
        }

        // Checked before anything is read out of the body. The server states the shape it wrote,
        // and a reader that guesses instead of checking is exactly what this field exists to
        // prevent — which is why it has to be honoured by the first SDK that ships, not by a
        // later one: only code already on the device can refuse.
        //
        // No stamp at all is not a refusal. A body without one is, by construction, the shape that
        // existed before the stamp did, which is the shape this reader was written against;
        // refusing it would switch remote configuration silently off against a server that merely
        // predates the field. Only a stamp we can see and do not recognise is a reason to refuse.
        // A stamp that is not a number is not a stamp: optInt would quietly turn the string "1"
        // into 1 and accept a body the other SDKs refuse, and the point of this field is that
        // every reader agrees about the same response.
        val stamped = json.has(FIELD_SCHEMA_VERSION) && !json.isNull(FIELD_SCHEMA_VERSION)
        if (stamped &&
            (
                json.opt(FIELD_SCHEMA_VERSION) !is Number ||
                    json.optInt(FIELD_SCHEMA_VERSION, SCHEMA_VERSION_ABSENT) != SUPPORTED_SCHEMA_VERSION
                )
        ) {
            logUnsupportedSchema(json.optInt(FIELD_SCHEMA_VERSION, SCHEMA_VERSION_ABSENT))
            return Outcome.UNSUPPORTED_SCHEMA
        }

        val ttl = json.optLong(FIELD_TTL, DEFAULT_TTL_SECONDS)
        val enabled = json.optBoolean(FIELD_ENABLED, false)
        val activation = json.optString(FIELD_ACTIVATION, ACTIVATION_NEXT_SESSION)
        refreshOnForeground = json.optBoolean(FIELD_REFRESH_ON_FOREGROUND, false)

        val before = RemoteConfigValues(store.sessionSampleRate())
        val version = json.optInt(FIELD_VERSION, 0).takeIf { it > 0 }
        val after = if (enabled) {
            readValues(json.optJSONObject(FIELD_RUM)).copy(
                version = version,
                // Stored as the raw string: the platform's job is delivery, the meaning belongs to
                // the host application.
                custom = json.optJSONObject(FIELD_CUSTOM)?.toString(),
                etag = etag
            )
        } else {
            EMPTY_VALUES.copy(version = version, etag = etag)
        }
        store.store(after)

        if (activation == ACTIVATION_IMMEDIATE && changesThisClient(before, after)) {
            restartSession()
        }

        // Remembered here rather than around the request, so a fetch that fails keeps the ttl the
        // server last asked for instead of falling back to ours.
        currentTtlSeconds = if (ttl > 0) ttl else DEFAULT_TTL_SECONDS
        return Outcome.APPLIED
    }

    private fun readValues(rum: JSONObject?): RemoteConfigValues {
        if (rum == null) return EMPTY_VALUES
        return RemoteConfigValues(
            sessionSampleRate = readRate(rum)
        )
    }

    /**
     * A value the response did not send stays absent, so the value passed to init keeps applying.
     * An out-of-range number is treated the same way rather than clamped: a rate we cannot trust is
     * not a rate to sample a customer's traffic with.
     */
    private fun readRate(rum: JSONObject): Float? {
        if (!rum.has(FIELD_SESSION_SAMPLE_RATE)) return null
        val rate = rum.optDouble(FIELD_SESSION_SAMPLE_RATE, Double.NaN)
        return if (rate.isNaN() || rate < 0.0 || rate > MAX_RATE) null else rate.toFloat()
    }

    private fun changesThisClient(before: RemoteConfigValues, after: RemoteConfigValues): Boolean =
        (before.sessionSampleRate ?: initialSessionSampleRate) !=
            (after.sessionSampleRate ?: initialSessionSampleRate)

    private fun logUnreadableBody(e: JSONException) {
        sdkCore.internalLogger.log(
            InternalLogger.Level.DEBUG,
            InternalLogger.Target.MAINTAINER,
            { UNREADABLE_BODY_MESSAGE },
            e
        )
    }

    private fun logUnsupportedSchema(received: Int) {
        sdkCore.internalLogger.log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            { UNSUPPORTED_SCHEMA_MESSAGE.format(received, SUPPORTED_SCHEMA_VERSION) }
        )
    }

    private fun logFetchFailure(e: Throwable) {
        sdkCore.internalLogger.log(
            InternalLogger.Level.DEBUG,
            InternalLogger.Target.MAINTAINER,
            { FETCH_FAILED_MESSAGE },
            e
        )
    }

    private fun logScheduleRejected(e: RejectedExecutionException) {
        sdkCore.internalLogger.log(
            InternalLogger.Level.DEBUG,
            InternalLogger.Target.MAINTAINER,
            { "Remote configuration refresh not scheduled: executor is shutting down." },
            e
        )
    }

    companion object {
        internal const val DEFAULT_TTL_SECONDS = 300L
        internal const val ACTIVATION_NEXT_SESSION = "next_session"
        internal const val ACTIVATION_IMMEDIATE = "immediate"

        private val RETRY_DELAYS_SECONDS = longArrayOf(5L, 60L)

        private const val MAX_RATE = 100.0
        private const val MILLIS_PER_SECOND = 1_000L
        private const val JITTER_FRACTION = 0.2
        private const val FIELD_SCHEMA_VERSION = "schema_version"
        private const val FIELD_VERSION = "version"
        private const val FIELD_REFRESH_ON_FOREGROUND = "refresh_on_foreground"
        private const val FIELD_TTL = "ttl"
        private const val FIELD_ENABLED = "enabled"
        private const val FIELD_ACTIVATION = "activation"
        private const val FIELD_CUSTOM = "custom"
        private const val FIELD_RUM = "rum"
        private const val FIELD_SESSION_SAMPLE_RATE = "sessionSampleRate"

        private val EMPTY_VALUES = RemoteConfigValues(null)

        private const val HTTP_NOT_MODIFIED = 304
        private const val HEADER_ETAG = "ETag"
        private const val HEADER_IF_NONE_MATCH = "If-None-Match"

        /**
         * The contract this SDK reads. It is not the SDK version and not the settings version:
         * it names the SHAPE of the body, and the server bumps it only when a body would be
         * misread by a reader written against the previous shape.
         */
        internal const val SUPPORTED_SCHEMA_VERSION = 1
        private const val SCHEMA_VERSION_ABSENT = 0

        internal const val UNREADABLE_BODY_MESSAGE =
            "The remote configuration response was not readable; keeping the values already in use."

        internal const val UNSUPPORTED_SCHEMA_MESSAGE =
            "Ignoring a remote configuration written to schema version %d; this SDK reads version" +
                " %d. Update the SDK to take the console's settings again."

        internal const val FETCH_FAILED_MESSAGE =
            "Unable to refresh the remote configuration; keeping the values already in use."

        /**
         * Where to ask. A custom endpoint means the app was pointed at the customer's own host for
         * the RUM intake, and the configuration lives beside it there — which is exactly the layout
         * the private-deployment nginx template serves.
         *
         * The SDK version rides along purely as information: it keys nothing on this side (see the
         * store key), and the server may one day target a configuration at a range of them.
         */
        fun buildConfigUrl(
            intakeUrl: String,
            clientToken: String,
            env: String,
            appVersion: String,
            sdkVersion: String
        ): String {
            val parameters = buildString {
                append("?client_token=").append(encode(clientToken))
                append("&sdk=android")
                if (env.isNotEmpty()) append("&env=").append(encode(env))
                if (appVersion.isNotEmpty()) append("&app_version=").append(encode(appVersion))
                if (sdkVersion.isNotEmpty()) append("&sdk_version=").append(encode(sdkVersion))
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

        /**
         * Spreads a delay by ±20%. An endpoint incident aligns every failed client's retry clock to
         * the same moment; without this, recovery would be greeted by the whole fleet at once,
         * exactly when the endpoint is weakest.
         */
        internal fun jittered(delaySeconds: Long, randomFraction: Double): Long =
            (delaySeconds * (1 - JITTER_FRACTION + 2 * JITTER_FRACTION * randomFraction)).toLong()
    }
}
