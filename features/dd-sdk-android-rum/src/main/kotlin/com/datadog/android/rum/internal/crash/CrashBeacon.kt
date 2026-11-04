/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.crash

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.scope.RumSessionScope
import com.datadog.android.rum.internal.domain.scope.toErrorSchemaType
import com.datadog.android.rum.model.ErrorEvent
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.InterruptedIOException
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * FLASHCAT FORK - the crash beacon.
 *
 * When the app crashes in the foreground, a beacon of a few hundred bytes, without stack, is sent
 * while the crash ends the process, so the crash is counted even if the app is never opened again.
 * The full report still follows on the next launch, under [crashId] as `error.id`, and carries what
 * became of the beacon in `error.beacon`. The intake merges the two by that id.
 *
 * A JVM crash reaches us from an uncaught exception handler. It can use ordinary JVM code, but
 * reading SDK state can still block. Preparation and sending therefore run on the beacon thread,
 * before the crash is written, which lets the written crash carry the beacon's outcome.
 * The wait is bounded, so a beacon that hangs costs the crash at
 * most the configured timeout before it goes on to be written and handed to the previous handler.
 *
 * The request runs on a thread created at startup: a crash caused by running out of memory or
 * threads must not depend on creating one.
 */
internal class CrashBeacon(
    /** `<RUM intake>/crash?client_token=...`. */
    private val url: String,
    private val applicationId: String,
    private val callFactory: Call.Factory,
    private val executor: ExecutorService,
    private val internalLogger: InternalLogger,
    /** Updated outside the crash handler; reading these values cannot wait on preferences. */
    private val configuration: Configuration,
    /** Whether the app is in the foreground right now, see [isUserVisible]. */
    private val isInForeground: () -> Boolean,
    private val hasCustomErrorEventMapper: Boolean = false,
    private val clock: () -> Long = { TimeUnit.NANOSECONDS.toMillis(System.nanoTime()) }
) {

    /**
     * The id this process's crash is reported under, drawn at startup rather than at the crash.
     * Only the first crash of a process gets it - see [send].
     */
    val crashId: String = UUID.randomUUID().toString()

    private val claimed = AtomicBoolean(false)

    init {
        // Started now: a crash caused by running out of memory or threads cannot start one.
        if (!hasCustomErrorEventMapper) (executor as? ThreadPoolExecutor)?.prestartCoreThread()
    }

    /**
     * Whether a crash may send a beacon as far as the console and the configuration say, and how
     * long it waits for one: what a native crash, which cannot ask at the crash, is armed with ahead
     * of it. Reads only in-memory fields.
     */
    fun armState(): Pair<Boolean, Int> {
        val enabled = configuration.enabled != false && !hasCustomErrorEventMapper
        return enabled to timeoutMs().toInt()
    }

    private fun timeoutMs(): Long =
        (configuration.timeoutMs?.takeIf { it in MIN_TIMEOUT_MS..MAX_TIMEOUT_MS } ?: DEFAULT_TIMEOUT_MS).toLong()

    /** Stops the beacon thread with the SDK. */
    fun shutdown() {
        executor.shutdownNow()
    }

    /**
     * Sends the beacon of the crash happening now, waits for it within the timeout, and tells what
     * became of it. The crash is then written under [crashId].
     *
     * The crashed thread only submits and waits: reading the SDK context, deciding whether to send,
     * building and sending the request all run on the beacon thread, inside the bound. Nothing here
     * throws, so a failing beacon can never cost the crash its report.
     *
     * Returns null for any crash after the first one of this process, which a host handler can let
     * survive: one id counts one crash, so later crashes are written under ids of their own and send
     * no beacon.
     *
     * @param datadogContext reads the SDK context, with the RUM feature context, at the crash.
     * @param crashType the exception class, or the signal name of a native crash.
     * @param crashTimeMs the crash time on the device clock, the one the crash is written with.
     * @param timeSinceAppStartMs the crash time since the process started, as written with the crash.
     * @param onCrashId called with [crashId] before anything is sent, for a crash whose report is
     * written after the beacon and has to know the id first. If it throws, nothing is sent.
     */
    @Suppress("TooGenericExceptionCaught", "LongParameterList", "ReturnCount")
    fun send(
        datadogContext: () -> DatadogContext?,
        crashType: String,
        message: String,
        crashTimeMs: Long,
        timeSinceAppStartMs: Long,
        onCrashId: (String) -> Unit = {}
    ): Result? {
        if (!claimed.compareAndSet(false, true)) return null
        // The full ErrorEvent is mapped later, on the persistence thread. Calling the user's
        // mapper twice, or with a partial event, cannot preserve its filtering semantics.
        if (hasCustomErrorEventMapper) return Result(ErrorEvent.Beacon(STATUS_SKIPPED, 0L), null)
        // Decided here, on the crashed thread, before anything is submitted: a background crash, or
        // one the console turned beacons off for, must not wait on the beacon thread at all.
        if (configuration.enabled == false || !safeIsInForeground()) {
            return Result(ErrorEvent.Beacon(STATUS_SKIPPED, 0L), null)
        }
        val start = clock()
        try {
            onCrashId(crashId)
        } catch (e: Throwable) {
            // The report would not carry the id a sent beacon counted the crash under: one crash
            // would be counted twice. Better not to send.
            return Result(ErrorEvent.Beacon(status = STATUS_FAILED, duration = clock() - start), null)
        }
        val timeoutMs = timeoutMs()
        val dispatch = AtomicReference<Dispatch>(Dispatch.Pending)
        var future: Future<String>? = null
        val status = try {
            @Suppress("UnsafeThirdPartyFunctionCall") // every failure is caught below
            val submitted = executor.submit<String> {
                try {
                    attempt(datadogContext, crashType, message, crashTimeMs, timeSinceAppStartMs, timeoutMs, dispatch)
                } catch (e: Throwable) {
                    // Logging belongs inside the bound too: a logger can itself wait on SDK state.
                    internalLogger.log(
                        InternalLogger.Level.WARN,
                        InternalLogger.Target.MAINTAINER,
                        { SEND_FAILED_MESSAGE },
                        e
                    )
                    STATUS_FAILED
                }
            }
            future = submitted
            @Suppress("UnsafeThirdPartyFunctionCall") // every failure is caught below
            submitted.get(maxOf(0L, timeoutMs - (clock() - start)), TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            STATUS_TIMEOUT
        } catch (e: Throwable) {
            // Out of memory is a common way to crash; nothing about a beacon may make it worse.
            STATUS_FAILED
        }
        // Claim the timestamp even on timeout if a request may already have left. Conversely, a
        // context read that resumes after this point must not send a beacon without that timestamp
        // on the report. The CAS in attempt() makes these two outcomes mutually exclusive.
        val crashDate = (dispatch.getAndSet(Dispatch.Finished) as? Dispatch.Ready)?.date
        future?.cancel(true)
        val duration = if (status == STATUS_SKIPPED) 0L else clock() - start
        return Result(ErrorEvent.Beacon(status = status, duration = duration), crashDate)
    }

    /** Runs on the beacon thread. */
    @Suppress("ReturnCount", "LongParameterList")
    private fun attempt(
        readContext: () -> DatadogContext?,
        crashType: String,
        message: String,
        crashTimeMs: Long,
        timeSinceAppStartMs: Long,
        timeoutMs: Long,
        dispatch: AtomicReference<Dispatch>
    ): String {
        val datadogContext = readContext() ?: return STATUS_FAILED
        val rumContext = RumContext.fromFeatureContext(
            datadogContext.featuresContext[Feature.RUM_FEATURE_NAME].orEmpty()
        )
        // Only a crash in a session RUM tracks: the crash is written for those alone, and a beacon
        // without one would count a crash that never reaches the crash list or the rate.
        if (configuration.enabled == false ||
            datadogContext.trackingConsent != TrackingConsent.GRANTED ||
            !rumContext.isSessionActive ||
            (
                rumContext.sessionState != RumSessionScope.State.TRACKED &&
                    rumContext.sessionState != RumSessionScope.State.WITHHELD
                ) ||
            !isInForeground()
        ) {
            return STATUS_SKIPPED
        }
        val offset = if (rumContext.viewId != null) {
            rumContext.viewTimestampOffset
        } else {
            datadogContext.time.serverTimeOffsetMs
        }
        val date = crashTimeMs + offset
        val body = payload(datadogContext, rumContext, crashType, message, date, timeSinceAppStartMs)
        if (!dispatch.compareAndSet(Dispatch.Pending, Dispatch.Ready(date))) return STATUS_TIMEOUT
        return post(body, timeoutMs)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun safeIsInForeground(): Boolean = try {
        isInForeground()
    } catch (e: Throwable) {
        false
    }

    private fun post(body: String, timeoutMs: Long): String {
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val call = callFactory.newCall(request)
        // A little under the crashed thread's wait, so the outcome is known before it stops waiting.
        call.timeout().timeout(maxOf(MIN_CALL_TIMEOUT_MS, timeoutMs - CALL_TIMEOUT_MARGIN_MS), TimeUnit.MILLISECONDS)
        return try {
            call.execute().use { response ->
                if (response.isSuccessful) STATUS_SENT else STATUS_FAILED
            }
        } catch (e: InterruptedIOException) {
            STATUS_TIMEOUT
        }
    }

    @Suppress("LongParameterList")
    internal fun payload(
        datadogContext: DatadogContext,
        rumContext: RumContext,
        crashType: String,
        message: String,
        date: Long,
        timeSinceAppStartMs: Long
    ): String {
        // This exact corrected date is also passed to the crash report. A view change or clock
        // sync while the request is in flight must not change the ledger's launch-date key.
        val deviceInfo = datadogContext.deviceInfo
        val user = datadogContext.userInfo
        return JSONObject().apply {
            put("crash_id", crashId)
            put("application_id", applicationId)
            put("launch_time", date - timeSinceAppStartMs)
            put("date", date)
            put("crash_type", crashType)
            put("crash_message", message.take(MAX_MESSAGE_LENGTH))
            put("session_id", rumContext.sessionId)
            putOpt("view_id", rumContext.viewId)
            putOpt("view_url", rumContext.viewUrl)
            putOpt("view_name", rumContext.viewName)
            putOpt("usr_anonymous_id", user.anonymousId)
            putOpt("usr_id", user.id)
            put("version", datadogContext.version)
            put("env", datadogContext.env)
            put("service", datadogContext.service)
            put("os_name", deviceInfo.osName)
            put("os_version", deviceInfo.osVersion)
            put("device_model", deviceInfo.deviceModel)
            put("device_type", deviceInfo.deviceType.toErrorSchemaType().toJson().asString)
            put("sdk_version", datadogContext.sdkVersion)
            put("source", datadogContext.source)
        }.toString()
    }

    internal data class Result(val beacon: ErrorEvent.Beacon, val crashDate: Long?)

    internal class Configuration(
        @Volatile var enabled: Boolean? = null,
        @Volatile var timeoutMs: Int? = null
    )

    private sealed class Dispatch {
        object Pending : Dispatch()
        data class Ready(val date: Long) : Dispatch()
        object Finished : Dispatch()
    }

    internal companion object {
        internal const val STATUS_SENT = "sent"
        internal const val STATUS_TIMEOUT = "timeout"
        internal const val STATUS_FAILED = "failed"
        internal const val STATUS_SKIPPED = "skipped"

        internal const val DEFAULT_TIMEOUT_MS = 1_000

        /**
         * The foreground rule of crash beacons, the same for JVM and native crashes: a process the
         * user can see - in front, or visible in split screen or picture in picture. A foreground
         * service alone does not count: there is no screen the user saw crash.
         */
        fun isUserVisible(importance: Int): Boolean =
            importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
                importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
        private const val MIN_TIMEOUT_MS = 500
        private const val MAX_TIMEOUT_MS = 2_000
        private const val CALL_TIMEOUT_MARGIN_MS = 100L
        private const val MIN_CALL_TIMEOUT_MS = 100L
        private const val MAX_MESSAGE_LENGTH = 256

        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        internal const val SEND_FAILED_MESSAGE = "Unable to send the crash beacon."

        /** The crash beacon goes next to the RUM intake, like the remote configuration. */
        fun buildUrl(intakeUrl: String, clientToken: String): String =
            intakeUrl.trimEnd('/') + "/crash?client_token=" + URLEncoder.encode(clientToken, "UTF-8")
    }
}
