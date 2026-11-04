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

/**
 * FLASHCAT FORK - the crash beacon.
 *
 * When the app crashes in the foreground, a beacon of a few hundred bytes, without stack, is sent
 * while the crash ends the process, so the crash is counted even if the app is never opened again.
 * The full report still follows on the next launch, under [crashId] as `error.id`, and carries what
 * became of the beacon in `error.beacon`. The intake merges the two by that id.
 *
 * A JVM crash reaches us from an uncaught exception handler, after the throwing thread unwound, so
 * no lock is held on its behalf: unlike a signal handler, building and sending the request here is
 * safe. The beacon is therefore sent before the crash is written, which is what lets the written
 * crash carry the beacon's outcome. The wait is bounded, so a beacon that hangs costs the crash at
 * most [timeoutMs] before it goes on to be written and handed to the previous handler.
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
    /** The console's switch, read at the crash: it can turn beacons off in a running process. */
    private val remoteEnabled: () -> Boolean?,
    /** The console's wait, read at the crash; null keeps [DEFAULT_TIMEOUT_MS]. */
    private val remoteTimeoutMs: () -> Int?,
    /** Whether the process is in the foreground right now. */
    private val isInForeground: () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis
) {

    /**
     * The id this process's crash is reported under, drawn at startup rather than at the crash.
     * Only the first crash of a process gets it - see [send].
     */
    val crashId: String = UUID.randomUUID().toString()

    private val claimed = AtomicBoolean(false)

    init {
        // Started now: a crash caused by running out of memory or threads cannot start one.
        (executor as? ThreadPoolExecutor)?.prestartCoreThread()
    }

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
     * @param crashTimeMs the crash time on the device clock, the one the crash is written with.
     * @param timeSinceAppStartMs the crash time since the process started, as written with the crash.
     */
    @Suppress("TooGenericExceptionCaught")
    fun send(
        datadogContext: () -> DatadogContext?,
        throwable: Throwable,
        message: String,
        crashTimeMs: Long,
        timeSinceAppStartMs: Long
    ): ErrorEvent.Beacon? {
        if (!claimed.compareAndSet(false, true)) return null
        val start = clock()
        val timeoutMs = try {
            (remoteTimeoutMs() ?: DEFAULT_TIMEOUT_MS).toLong()
        } catch (e: Throwable) {
            DEFAULT_TIMEOUT_MS.toLong()
        }
        val status = try {
            @Suppress("UnsafeThirdPartyFunctionCall") // every failure is caught below
            val future: Future<String> = executor.submit<String> {
                attempt(datadogContext, throwable, message, crashTimeMs, timeSinceAppStartMs, timeoutMs)
            }
            @Suppress("UnsafeThirdPartyFunctionCall") // every failure is caught below
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            STATUS_TIMEOUT
        } catch (e: Throwable) {
            // Out of memory is a common way to crash; nothing about a beacon may make it worse.
            logFailure(e)
            STATUS_FAILED
        }
        val duration = if (status == STATUS_SKIPPED) 0L else clock() - start
        return ErrorEvent.Beacon(status = status, duration = duration)
    }

    /** Runs on the beacon thread. */
    @Suppress("ReturnCount", "LongParameterList")
    private fun attempt(
        readContext: () -> DatadogContext?,
        throwable: Throwable,
        message: String,
        crashTimeMs: Long,
        timeSinceAppStartMs: Long,
        timeoutMs: Long
    ): String {
        val datadogContext = readContext() ?: return STATUS_FAILED
        val rumContext = RumContext.fromFeatureContext(
            datadogContext.featuresContext[Feature.RUM_FEATURE_NAME].orEmpty()
        )
        // Only a crash in a session RUM tracks: the crash is written for those alone, and a beacon
        // without one would count a crash that never reaches the crash list or the rate.
        if (remoteEnabled() == false ||
            datadogContext.trackingConsent != TrackingConsent.GRANTED ||
            rumContext.sessionState != RumSessionScope.State.TRACKED ||
            !isInForeground()
        ) {
            return STATUS_SKIPPED
        }
        val body = payload(datadogContext, rumContext, throwable, message, crashTimeMs, timeSinceAppStartMs)
        return post(body, timeoutMs)
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
        throwable: Throwable,
        message: String,
        crashTimeMs: Long,
        timeSinceAppStartMs: Long
    ): String {
        // Both on the device clock corrected by the server offset, like the crash written next: the
        // intake takes the report's launch as date minus time_since_app_start, and the two writes of
        // one crash only merge if they land on the same launch day.
        val date = crashTimeMs + datadogContext.time.serverTimeOffsetMs
        val deviceInfo = datadogContext.deviceInfo
        val user = datadogContext.userInfo
        return JSONObject().apply {
            put("crash_id", crashId)
            put("application_id", applicationId)
            put("launch_time", date - timeSinceAppStartMs)
            put("date", date)
            put("crash_type", throwable.javaClass.canonicalName ?: throwable.javaClass.simpleName)
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

    private fun logFailure(e: Throwable) {
        internalLogger.log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            { SEND_FAILED_MESSAGE },
            e
        )
    }

    internal companion object {
        internal const val STATUS_SENT = "sent"
        internal const val STATUS_TIMEOUT = "timeout"
        internal const val STATUS_FAILED = "failed"
        internal const val STATUS_SKIPPED = "skipped"

        internal const val DEFAULT_TIMEOUT_MS = 1_000
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
