/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.internal.active

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.context.UserInfo
import com.datadog.android.api.net.RequestFactory
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.internal.utils.executeSafe
import com.datadog.android.core.internal.utils.scheduleSafe
import com.datadog.android.privacy.TrackingConsent
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Independent, best-effort active reporting. User/deadline state belongs to the worker;
 * consent, dedup, calls, invalidation and retry ownership are protected by lock across context/worker threads.
 * Volatile foreground/storage flags and the pending snapshot may also change during invalidation.
 */
// Consent withdrawal and clearAllData attempt synchronous deletion; failed storage is disabled for this instance.
@SuppressLint("ApplySharedPref")
internal class ActiveBeaconController(
    private val core: InternalSdkCore,
    context: DatadogContext,
    appContext: Context,
    applicationId: String,
    intakeUrl: String,
    private val trackAnonymous: Boolean,
    private val executor: ScheduledExecutorService,
    private val callFactory: Call.Factory,
    processName: String,
    private val wallClock: () -> Long = System::currentTimeMillis
) {
    private val sdkContext = context
    private val endpoint = buildEndpoint(intakeUrl)

    // Opening preferences may hit disk; defer it until the worker needs to read/write, or consent is revoked.
    private val prefs by lazy {
        try {
            appContext.getSharedPreferences(
                storeName(core.name, applicationId, intakeUrl, processName),
                Context.MODE_PRIVATE
            )
        } catch (e: Exception) {
            log("Active beacon storage is unavailable.", e)
            null
        }
    }
    private val bootId by lazy { readBootId() }
    private val lock = Any()

    // Wait for the context-thread snapshot, which is ordered with all consent changes.
    @Volatile private var consent = TrackingConsent.PENDING

    @Volatile private var stopped = false
    private var call: Call? = null

    @Volatile private var foreground = false
    private var user = context.userInfo
    private var sentDay = ""
    private val sent = mutableSetOf<String>()
    @Volatile private var generation = 0L
    private var nextAllowedAt = 0L
    private var midnightTask: ScheduledFuture<*>? = null
    private var midnightDeadline = 0L
    private var midnightClockOffset = 0L
    private var midnightGeneration = 0L
    private var retryTask: ScheduledFuture<*>? = null
    private var retryDeadline = 0L
    private var retryGeneration = 0L
    @Volatile private var storageUsable = true

    @Volatile private var pendingInMemory: Pending? = null

    init {
        // This executor belongs only to active reporting. Replaced deadlines must leave no canceled queue entries.
        (executor as? ScheduledThreadPoolExecutor)?.removeOnCancelPolicy = true
    }

    fun onForeground(value: Boolean) {
        foreground = value
        if (!value) return
        val request = captureRequest()
        executor.executeSafe("active foreground", core.internalLogger) {
            trigger(request)
            scheduleMidnight()
        }
    }

    fun onUserInfo(value: UserInfo, currentConsent: TrackingConsent) {
        updateConsent(currentConsent)
        val request = captureRequest()
        executor.executeSafe("active user", core.internalLogger) {
            user = value
            trigger(request)
        }
    }

    fun onConsent(value: TrackingConsent) {
        updateConsent(value)
        if (value != TrackingConsent.GRANTED) return
        val request = captureRequest()
        executor.executeSafe("active consent", core.internalLogger) { trigger(request) }
    }

    // The worker also runs blocking requests, so a trigger may only execute after the app has left.
    // Lock-free: called on the main thread, while lock holders may be doing storage I/O. Read generation
    // before consent; consent is written before generation++, so a stale pair always fails savePending.
    private fun captureRequest(): TriggerRequest {
        val capturedGeneration = generation
        return TriggerRequest(foreground, consent, capturedGeneration, wallClock(), SystemClock.elapsedRealtime())
    }

    private fun updateConsent(value: TrackingConsent) {
        synchronized(lock) {
            // Every setUserInfo repeats the consent. Nothing is written without GRANTED, so a repeated
            // non-granted value only needs work when the previous deletion failed.
            if (value == consent && value != TrackingConsent.GRANTED &&
                (value == TrackingConsent.PENDING || storageUsable)
            ) {
                return
            }
            consent = value
            if (value != TrackingConsent.GRANTED) {
                generation++
                call?.cancel()
                cancelRetryLocked()
            }
            if (value == TrackingConsent.NOT_GRANTED) {
                pendingInMemory = null
                sentDay = ""
                sent.clear()
                // Clear synchronously so a stopped executor cannot leave consent-withdrawn data behind.
                clearStorage()
            }
        }
        if (value == TrackingConsent.NOT_GRANTED) {
            executor.executeSafe("active revoke", core.internalLogger) {
                nextAllowedAt = 0L
            }
        }
    }

    fun clearData() {
        synchronized(lock) {
            generation++
            call?.cancel()
            cancelRetryLocked()
            pendingInMemory = null
            sentDay = ""
            sent.clear()
            clearStorage()
        }
        executor.executeSafe("active clear", core.internalLogger) {
            nextAllowedAt = 0L
        }
    }

    fun stop() {
        synchronized(lock) {
            stopped = true
            generation++
            call?.cancel()
            cancelRetryLocked()
        }
        executor.shutdownNow()
    }

    private fun trigger(request: TriggerRequest) {
        val inForeground = foreground
        if (!inForeground) {
            if (request.foreground) deferQueuedTrigger(request)
            return
        }
        val workGeneration = synchronized(lock) {
            if (!eligible(inForeground)) return
            generation
        }
        val now = wallClock()
        val elapsed = SystemClock.elapsedRealtime()
        val day = day(now)
        val key = identity(sdkContext.version, sdkContext.versionCode, currentUser())
        val body = buildBody(sdkContext, currentUser()) ?: return
        nextAllowedAt = maxOf(nextAllowedAt, withStorage(0L) { it.getLong("next_allowed", 0) })
        if (now < nextAllowedAt) {
            val pending = readPending(now)
            if (!wasSent(key, day) && (pending == null || pending.day != day || pending.identity != key)) {
                savePending(body, key, day, now, elapsed, workGeneration)
            }
            if (pending != null || !wasSent(key, day)) scheduleRetry(0, nextAllowedAt, workGeneration)
            return
        }
        synchronized(lock) {
            if (generation != workGeneration) return
            // A new trigger can start a new round, but the previous round must no longer run.
            cancelRetryLocked()
        }
        val pending = readPending(now)
        if (pending != null) {
            if (!send(pending.body, pending.age, pending.identity, pending.day, 0, true, workGeneration)) {
                if (!wasSent(key, day) && (pending.day != day || pending.identity != key)) {
                    savePending(body, key, day, now, elapsed, workGeneration)
                    // The one-record queue now contains a beacon that has not been attempted yet.
                    if (foreground) scheduleRetry(0, maxOf(nextAllowedAt, now + 5_000), workGeneration)
                }
                return
            }
        }
        if (!send(body, 0, key, day, 0, false, workGeneration)) {
            synchronized(lock) {
                // This snapshot was captured while eligible in the foreground. Keep it if only
                // backgrounding prevented admission, without resetting an existing failure's age.
                val saved = pendingInMemory
                if (!foreground && (saved == null || saved.identity != key || saved.day != day)) {
                    savePending(body, key, day, now, elapsed, workGeneration)
                }
            }
        }
    }

    // Requested in the foreground, but it waited behind earlier work until the app had left: keep the
    // snapshot of that moment for replay, unless consent or clearAllData invalidated it meanwhile.
    private fun deferQueuedTrigger(request: TriggerRequest) {
        if (request.consent != TrackingConsent.GRANTED || !hasIdentity()) return
        val day = day(request.wallTime)
        val key = identity(sdkContext.version, sdkContext.versionCode, currentUser())
        if (wasSent(key, day)) return
        val body = buildBody(sdkContext, currentUser()) ?: return
        val pending = readPending(wallClock())
        if (pending != null && pending.identity == key && pending.day == day) return
        savePending(body, key, day, request.wallTime, request.elapsed, request.generation)
    }

    /** Returns true when it is safe to continue with a newer beacon. */
    private fun send(
        body: String,
        age: Long,
        key: String,
        day: String,
        attempt: Int,
        pending: Boolean,
        expectedGeneration: Long
    ): Boolean {
        if (!eligible()) return false
        val json = JSONObject(body).put("age_ms", age)
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_BYTES) {
            log("Active beacon exceeds the size limit.")
            if (pending) clearPending()
            return true
        }
        val request = Request.Builder().url(endpoint)
            .header(RequestFactory.HEADER_API_KEY, sdkContext.clientToken)
            .post(bytes.toRequestBody(JSON)).build()
        val version: Long
        val activeCall: Call
        synchronized(lock) {
            // Request admission is the last foreground check; an admitted call may finish in the background.
            if (stopped || !foreground || consent != TrackingConsent.GRANTED || generation != expectedGeneration) {
                return false
            }
            // A process may stop after the sent marker was committed but before pending was removed.
            if (wasSent(key, day)) {
                if (pending) clearPending()
                return true
            }
            version = generation
            activeCall = callFactory.newCall(request)
            call = activeCall
        }
        val requestStartedAt = wallClock()
        val requestStartedElapsed = SystemClock.elapsedRealtime()
        var responseRetryAt = 0L
        val result = try {
            activeCall.execute().use { response ->
                when {
                    response.isSuccessful -> Result.SUCCESS
                    response.code == 429 -> {
                        responseRetryAt = retryAfter(
                            response.header("Flashcat-Request-Rate-Limit-Delay"),
                            response.header("Retry-After"),
                            wallClock()
                        )
                        Result.RATE_LIMITED
                    }
                    response.code == 408 || response.code in 500..599 -> Result.RETRY
                    else -> Result.PERMANENT
                }
            }
        } catch (e: IOException) {
            Result.RETRY
        } finally {
            synchronized(lock) { if (call === activeCall) call = null }
        }
        synchronized(lock) {
            if (stopped || consent != TrackingConsent.GRANTED || version != generation) return false
            when (result) {
                Result.SUCCESS -> {
                    recordSent(key, day)
                    if (pending) clearPending()
                    return true
                }
                Result.PERMANENT -> {
                    log("Active beacon rejected by the server.")
                    recordSent(key, day)
                    if (pending) clearPending()
                    return true
                }
                Result.RETRY, Result.RATE_LIMITED -> {
                    if (!pending) savePending(body, key, day, requestStartedAt, requestStartedElapsed, version)
                    if (result == Result.RATE_LIMITED) {
                        nextAllowedAt = responseRetryAt
                        withStorage(Unit) { it.edit().putLong("next_allowed", nextAllowedAt).apply() }
                    }
                    if (attempt < 2 && foreground) {
                        if (result == Result.RATE_LIMITED) {
                            scheduleRetry(attempt + 1, nextAllowedAt, version)
                        } else {
                            val delay = TimeUnit.SECONDS.toMillis(if (attempt == 0) 5L else 60L)
                            scheduleRetry(attempt + 1, wallClock() + delay, version)
                        }
                    }
                    return false
                }
            }
        }
    }

    private fun scheduleRetry(attempt: Int, deadline: Long, expectedGeneration: Long) {
        synchronized(lock) {
            if (stopped || consent != TrackingConsent.GRANTED || generation != expectedGeneration) return
            if (retryTask != null && retryDeadline == deadline) return
            cancelRetryLocked()
            retryDeadline = deadline
            val stamp = retryGeneration
            retryTask = executor.scheduleSafe(
                "active retry",
                (deadline - wallClock()).coerceAtLeast(1_000),
                TimeUnit.MILLISECONDS,
                core.internalLogger
            ) {
                synchronized(lock) {
                    if (generation != expectedGeneration || retryGeneration != stamp) return@scheduleSafe
                    retryTask = null
                    retryGeneration++ // A consumed callback cannot run again, even if invoked late.
                }
                retry(attempt, expectedGeneration)
            }
        }
    }

    // Caller holds lock: consent/clear may invalidate a queued task from the context thread.
    private fun cancelRetryLocked() {
        retryGeneration++
        retryTask?.cancel(false)
        retryTask = null
    }

    private fun retry(attempt: Int, workGeneration: Long) {
        if (!eligible()) return
        val now = wallClock()
        if (now < nextAllowedAt) {
            scheduleRetry(attempt, nextAllowedAt, workGeneration)
            return
        }
        val pending = readPending(now) ?: return
        send(pending.body, pending.age, pending.identity, pending.day, attempt, true, workGeneration)
    }

    private fun eligible(inForeground: Boolean = foreground): Boolean =
        !stopped && inForeground && consent == TrackingConsent.GRANTED && hasIdentity()

    private fun hasIdentity(): Boolean = (!trackAnonymous || !user.anonymousId.isNullOrEmpty()) &&
        (!currentUser().anonymousId.isNullOrEmpty() || !user.id.isNullOrEmpty())

    private fun currentUser(): UserInfo = if (trackAnonymous) user else user.copy(anonymousId = null)

    private fun scheduleMidnight() {
        if (!foreground || stopped) return
        val now = wallClock()
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val deadline = calendar.timeInMillis
        val clockOffset = now - SystemClock.elapsedRealtime()
        if (midnightTask != null && midnightDeadline == deadline &&
            kotlin.math.abs(clockOffset - midnightClockOffset) < 1_000
        ) return
        midnightTask?.cancel(false)
        midnightDeadline = deadline
        midnightClockOffset = clockOffset
        val stamp = ++midnightGeneration
        midnightTask = executor.scheduleSafe(
            "active midnight",
            (deadline - now).coerceAtLeast(1),
            TimeUnit.MILLISECONDS,
            core.internalLogger
        ) {
            if (stamp != midnightGeneration) return@scheduleSafe
            midnightTask = null
            if (foreground && !stopped) {
                trigger(captureRequest())
                scheduleMidnight()
            }
        }
    }
    private class TriggerRequest(
        val foreground: Boolean,
        val consent: TrackingConsent,
        val generation: Long,
        val wallTime: Long,
        val elapsed: Long
    )
    private data class Pending(
        val body: String,
        val identity: String,
        val day: String,
        val createdAt: Long,
        val elapsedAt: Long,
        val bootId: String?,
        val age: Long = 0
    )
    private fun readPending(now: Long): Pending? {
        pendingInMemory?.let {
            val age = pendingAge(now, SystemClock.elapsedRealtime(), it.createdAt, it.elapsedAt, bootId, it.bootId)
            if (age != null) return it.copy(age = age)
            clearPending()
        }
        return withStorage<Pending?>(null) { storage ->
            val body = storage.getString("body", null) ?: return@withStorage null
            val created = storage.getLong("created", 0)
            val elapsed = storage.getLong("elapsed", 0)
            val storedBootId = storage.getString("boot_id", null)
            val age = pendingAge(now, SystemClock.elapsedRealtime(), created, elapsed, bootId, storedBootId)
            if (age == null) {
                clearPending()
                return@withStorage null
            }
            Pending(
                body,
                storage.getString("identity", "") ?: "",
                storage.getString("day", "") ?: "",
                created,
                elapsed,
                storedBootId,
                age
            )
        }
    }
    private fun savePending(
        body: String,
        identity: String,
        day: String,
        created: Long,
        elapsed: Long = SystemClock.elapsedRealtime(),
        expectedGeneration: Long
    ) {
        synchronized(lock) {
            if (stopped || consent != TrackingConsent.GRANTED || generation != expectedGeneration) return
            cancelRetryLocked()
            pendingInMemory = Pending(body, identity, day, created, elapsed, bootId)
            withStorage(Unit) {
                it.edit().putString("body", body).putString("identity", identity).putString("day", day)
                    .putLong("created", created).putLong("elapsed", elapsed).putString("boot_id", bootId).apply()
            }
        }
    }
    private fun wasSent(key: String, day: String): Boolean = synchronized(lock) {
        loadSent()
        day == sentDay && key in sent
    }

    // Caller holds lock. Load only the current local day; an older replay must not replace today's set.
    private fun loadSent() {
        val today = day(wallClock())
        if (sentDay == today) return
        sentDay = today
        sent.clear()
        withStorage(Unit) {
            if (it.getString("sent_day", null) == today) {
                val identities = JSONArray(it.getString("sent_identities", null) ?: "[]")
                for (index in 0 until identities.length()) sent.add(identities.getString(index))
            }
        }
    }

    // Caller holds lock after validating the response generation. Disk writes stay on the worker.
    private fun recordSent(key: String, day: String) {
        loadSent()
        if (day != sentDay) return
        sent.add(key)
        withStorage(Unit) {
            // JSON escapes the identity separator and control characters before writing preference XML.
            val identities = JSONArray(sent).toString()
            if (!it.edit().putString("sent_day", sentDay).putString("sent_identities", identities).commit()) {
                log("Active beacon sent state could not be persisted; deduplication is memory only.")
            }
        }
    }

    private fun clearPending() {
        synchronized(lock) {
            cancelRetryLocked()
            pendingInMemory = null
            withStorage(Unit) {
                it.edit().remove("body").remove("identity").remove("day").remove("created").remove("elapsed")
                    .remove("boot_id").apply()
            }
        }
    }

    private inline fun <T> withStorage(fallback: T, block: (SharedPreferences) -> T): T {
        if (!storageUsable) return fallback
        return try {
            prefs?.let(block) ?: fallback
        } catch (e: Exception) {
            storageUsable = false
            log("Active beacon storage is unavailable; using memory only.", e)
            fallback
        }
    }

    private fun clearStorage() {
        // Retry deletion even if an earlier access failed. Never read potentially undeleted data
        // again in this controller until a synchronous deletion succeeds.
        try {
            val cleared = prefs?.edit()?.clear()?.commit() ?: false
            storageUsable = cleared
            if (!cleared) log("Active beacon storage could not be cleared; persisted replay is disabled.")
        } catch (e: Exception) {
            storageUsable = false
            log("Active beacon storage could not be cleared; persisted replay is disabled.", e)
        }
    }
    private fun log(message: String, error: Throwable? = null) {
        core.internalLogger.log(InternalLogger.Level.DEBUG, InternalLogger.Target.MAINTAINER, { message }, error)
    }
    private enum class Result { SUCCESS, PERMANENT, RATE_LIMITED, RETRY }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private const val MAX_BYTES = 2048
        private val MAX_AGE = TimeUnit.HOURS.toMillis(72)
        internal fun storeName(instance: String, applicationId: String, intake: String, process: String): String {
            val key = listOf(instance, applicationId, intake, process).joinToString("\u0000")
            val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
            return "dd_active_" + digest.joinToString("") { "%02x".format(it) }
        }
        // The app version and build are part of the key, as they are of the backend's: an upgrade
        // during the day is reported for the new version too. A key stored by an earlier SDK lacks
        // them and simply does not match, which costs one request.
        internal fun identity(version: String, versionCode: Int, user: UserInfo): String = listOf(
            version,
            versionCode.toString(),
            user.anonymousId ?: "",
            user.id ?: ""
        ).joinToString("\u0000")
        internal fun pendingAge(
            now: Long,
            elapsed: Long,
            created: Long,
            createdElapsed: Long,
            currentBootId: String?,
            storedBootId: String?
        ): Long? {
            if (createdElapsed > 0 && currentBootId != null &&
                currentBootId == storedBootId && elapsed >= createdElapsed
            ) {
                return (elapsed - createdElapsed).takeIf { it <= MAX_AGE }
            }
            val wallAge = now - created
            if (wallAge !in 0..MAX_AGE) return null
            if (currentBootId != null && storedBootId != null && currentBootId != storedBootId) return wallAge
            if (createdElapsed <= 0 || elapsed < createdElapsed) return wallAge // reboot or no monotonic sample
            val monotonicAge = elapsed - createdElapsed
            if (kotlin.math.abs(monotonicAge - wallAge) > TimeUnit.MINUTES.toMillis(2)) return null
            return monotonicAge.takeIf { it <= MAX_AGE }
        }
        private fun day(now: Long): String = Calendar.getInstance().apply { timeInMillis = now }.let {
            "${it.get(Calendar.YEAR)}-${it.get(Calendar.DAY_OF_YEAR)}"
        }
        internal fun retryAfter(flashcat: String?, value: String?, now: Long): Long {
            flashcat?.toLongOrNull()?.takeIf { it >= 0 }?.let {
                return now + TimeUnit.SECONDS.toMillis(it.coerceIn(1, 72 * 60 * 60))
            }
            value?.toLongOrNull()?.takeIf { it >= 0 }?.let {
                return now + TimeUnit.SECONDS.toMillis(it.coerceIn(1, 72 * 60 * 60))
            }
            val date = try {
                SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("GMT")
                    isLenient = false
                }.parse(value ?: "")?.time
            } catch (e: java.text.ParseException) { null }
            return (date ?: (now + 60_000)).coerceIn(now + 1_000, now + MAX_AGE)
        }
        private fun readBootId(): String? = try {
            File("/proc/sys/kernel/random/boot_id").readText().trim().takeIf { it.isNotEmpty() }
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        }
        internal fun buildEndpoint(intake: String): String {
            val url = intake.toHttpUrl()
            val forwarded = url.queryParameter("ddforward")
            if (forwarded != null) {
                val parts = forwarded.split('?', limit = 2)
                val path = parts[0].trimEnd('/') + "/active"
                val target = if (parts.size == 2) "$path?${parts[1]}" else path
                return url.newBuilder().setQueryParameter("ddforward", target).build().toString()
            }
            return url.newBuilder().encodedPath(url.encodedPath.trimEnd('/') + "/active").build().toString()
        }
        internal fun buildBody(context: DatadogContext, user: UserInfo): String? {
            // Byte bounds leave room for JSON escaping and age_ms while matching Doris column limits.
            fun bound(value: String?, limit: Int): String {
                val text = value ?: ""
                val out = StringBuilder()
                var bytes = 0
                var index = 0
                while (index < text.length) {
                    val cp = Character.codePointAt(text, index)
                    val part = String(Character.toChars(cp))
                    // Count JSON escaping too, so control characters cannot exceed the body budget.
                    val size = (JSONObject.quote(part).length - 2).coerceAtLeast(part.toByteArray(Charsets.UTF_8).size)
                    if (bytes + size > limit) break
                    out.append(part)
                    bytes += size
                    index += Character.charCount(cp)
                }
                return out.toString()
            }
            val json = JSONObject()
                .put("service", bound(context.service, 128)).put("env", bound(context.env, 128))
                .put("version", bound(context.version, 128)).put("build_version", context.versionCode.toString())
                .put("sdk_version", bound(context.sdkVersion, 64))
                .put("usr", JSONObject().put("id", bound(user.id, 255)).put("anonymous_id", user.anonymousId ?: ""))
                .put(
                    "os",
                    JSONObject().put("name", bound(context.deviceInfo.osName, 64))
                        .put("version", bound(context.deviceInfo.osVersion, 128))
                )
                .put(
                    "device",
                    JSONObject().put("type", context.deviceInfo.deviceType.name.lowercase(Locale.US))
                        .put(
                            "model",
                            bound(context.deviceInfo.deviceModel, 128)
                        ).put("brand", bound(context.deviceInfo.deviceBrand, 128))
                )
            return json.toString().takeIf { it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES - 32 }
        }
    }
}
