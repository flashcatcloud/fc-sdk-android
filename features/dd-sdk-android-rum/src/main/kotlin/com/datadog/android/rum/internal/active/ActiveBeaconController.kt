/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.internal.active

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.context.UserInfo
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.internal.utils.executeSafe
import com.datadog.android.core.internal.utils.scheduleSafe
import com.datadog.android.privacy.TrackingConsent
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** Independent, best-effort active reporting. All mutable state except consent/call is on executor. */
// Consent withdrawal and clearAllData must durably delete the pending identity before returning.
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
    processName: String
) {
    private val sdkContext = context
    private val endpoint = buildEndpoint(intakeUrl, context.clientToken)
    private val prefs = try {
        appContext.getSharedPreferences(
            storeName(core.name, applicationId, intakeUrl, processName),
            Context.MODE_PRIVATE
        )
    } catch (e: Exception) {
        log("Active beacon storage is unavailable.", e)
        null
    }
    private val lock = Any()

    // Wait for the context-thread snapshot, which is ordered with all consent changes.
    @Volatile private var consent = TrackingConsent.PENDING

    @Volatile private var stopped = false
    private var call: Call? = null

    @Volatile private var foreground = false
    private var user = context.userInfo
    private var sentDay = ""
    private val sent = mutableSetOf<String>()
    private var generation = 0L
    private var nextAllowedAt = 0L

    @Volatile private var pendingInMemory: Pending? = null

    fun start(isForeground: Boolean) {
        foreground = isForeground
        executor.executeSafe("active start", core.internalLogger) {
            trigger()
            scheduleMidnight()
        }
    }

    fun onForeground(value: Boolean) {
        foreground = value
        executor.executeSafe("active foreground", core.internalLogger) {
            if (value && foreground) {
                trigger()
                scheduleMidnight()
            }
        }
    }

    fun onUserInfo(value: UserInfo, currentConsent: TrackingConsent) {
        updateConsent(currentConsent)
        executor.executeSafe("active user", core.internalLogger) {
            user = value
            trigger()
        }
    }

    fun onConsent(value: TrackingConsent) {
        updateConsent(value)
        if (value == TrackingConsent.GRANTED) executor.executeSafe("active consent", core.internalLogger) { trigger() }
    }

    private fun updateConsent(value: TrackingConsent) {
        synchronized(lock) {
            consent = value
            if (value != TrackingConsent.GRANTED) {
                generation++
                call?.cancel()
                pendingInMemory = null
                // Clear synchronously so a stopped executor cannot leave consent-withdrawn data behind.
                prefs?.edit()?.clear()?.commit()
            }
        }
    }

    fun clearData() {
        synchronized(lock) {
            generation++
            call?.cancel()
            pendingInMemory = null
            prefs?.edit()?.clear()?.commit()
        }
        executor.executeSafe("active clear", core.internalLogger) {
            sent.clear()
            nextAllowedAt = 0L
        }
    }

    fun stop() {
        synchronized(lock) {
            stopped = true
            generation++
            call?.cancel()
        }
        executor.shutdownNow()
    }

    private fun trigger() {
        if (!eligible()) return
        val now = System.currentTimeMillis()
        val day = day(now)
        if (sentDay != day) {
            sentDay = day
            sent.clear()
        }
        val key = identity(currentUser())
        val body = buildBody(sdkContext, currentUser()) ?: return
        nextAllowedAt = maxOf(nextAllowedAt, prefs?.getLong("next_allowed", 0) ?: 0)
        if (now < nextAllowedAt) {
            savePending(body, key, day, now)
            return
        }
        val pending = readPending(now)
        if (pending != null) {
            if (pending.day == day && pending.identity == key) {
                clearPending()
            } else {
                if (!send(pending.body, pending.age, pending.identity, pending.day, 0, true)) {
                    if (eligible()) savePending(body, key, day, now)
                    return
                }
            }
        }
        if (!eligible() || key in sent) return
        send(body, 0, key, day, 0, false)
    }

    /** Returns true when it is safe to continue with a newer beacon. */
    private fun send(body: String, age: Long, key: String, day: String, attempt: Int, pending: Boolean): Boolean {
        if (!eligible()) return false
        val json = JSONObject(body).put("age_ms", age)
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_BYTES) {
            log("Active beacon exceeds the size limit.")
            if (pending) clearPending()
            return true
        }
        val request = Request.Builder().url(endpoint).post(bytes.toRequestBody(JSON)).build()
        val version: Long
        val activeCall: Call
        synchronized(lock) {
            if (stopped || consent != TrackingConsent.GRANTED) return false
            version = generation
            activeCall = callFactory.newCall(request)
            call = activeCall
        }
        val result = try {
            activeCall.execute().use { response ->
                when {
                    response.isSuccessful -> Result.SUCCESS
                    response.code == 429 -> {
                        nextAllowedAt = retryAfter(response.header("Retry-After"), System.currentTimeMillis())
                        Result.RATE_LIMITED
                    }
                    response.code in 500..599 -> Result.RETRY
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
                    if (day == sentDay) sent.add(key)
                    if (pending) clearPending()
                    return true
                }
                Result.PERMANENT -> {
                    log("Active beacon rejected by the server.")
                    if (day == sentDay) sent.add(key)
                    if (pending) clearPending()
                    return true
                }
                Result.RETRY, Result.RATE_LIMITED -> {
                    if (!pending) savePending(body, key, day, System.currentTimeMillis() - age)
                    if (result == Result.RATE_LIMITED) prefs?.edit()?.putLong("next_allowed", nextAllowedAt)?.apply()
                    if (result == Result.RETRY && attempt < 2 && foreground) {
                        val delay = if (attempt == 0) 5L else 60L
                        executor.scheduleSafe(
                            "active retry",
                            delay,
                            TimeUnit.SECONDS,
                            core.internalLogger
                        ) { retry(attempt + 1) }
                    }
                    return false
                }
            }
        }
    }

    private fun retry(attempt: Int) {
        if (!eligible()) return
        val now = System.currentTimeMillis()
        val pending = readPending(now) ?: return
        send(pending.body, pending.age, pending.identity, pending.day, attempt, true)
    }

    private fun eligible(): Boolean = !stopped && foreground && consent == TrackingConsent.GRANTED &&
        (!trackAnonymous || !user.anonymousId.isNullOrEmpty()) &&
        (!currentUser().anonymousId.isNullOrEmpty() || !user.id.isNullOrEmpty())

    private fun currentUser(): UserInfo = if (trackAnonymous) user else user.copy(anonymousId = null)

    private fun scheduleMidnight() {
        if (!foreground || stopped) return
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val stamp = ++midnightGeneration
        executor.scheduleSafe(
            "active midnight",
            (calendar.timeInMillis - now).coerceAtLeast(1),
            TimeUnit.MILLISECONDS,
            core.internalLogger
        ) {
            if (stamp == midnightGeneration && foreground && !stopped) {
                trigger()
                scheduleMidnight()
            }
        }
    }
    private var midnightGeneration = 0L

    private data class Pending(
        val body: String,
        val identity: String,
        val day: String,
        val createdAt: Long,
        val elapsedAt: Long,
        val age: Long = 0
    )
    private fun readPending(now: Long): Pending? {
        pendingInMemory?.let {
            val age = pendingAge(now, SystemClock.elapsedRealtime(), it.createdAt, it.elapsedAt)
            if (age != null) return it.copy(age = age)
            clearPending()
        }
        val body = prefs?.getString("body", null) ?: return null
        val created = prefs.getLong("created", 0)
        val elapsed = prefs.getLong("elapsed", 0)
        val age = pendingAge(now, SystemClock.elapsedRealtime(), created, elapsed)
        if (age == null) {
            clearPending()
            return null
        }
        return Pending(
            body,
            prefs.getString("identity", "") ?: "",
            prefs.getString("day", "") ?: "",
            created,
            elapsed,
            age
        )
    }
    private fun savePending(body: String, identity: String, day: String, created: Long) {
        synchronized(lock) {
            if (stopped || consent != TrackingConsent.GRANTED) return
            val elapsed = SystemClock.elapsedRealtime()
            pendingInMemory = Pending(body, identity, day, created, elapsed)
            prefs?.edit()?.putString("body", body)?.putString("identity", identity)?.putString("day", day)
                ?.putLong("created", created)?.putLong("elapsed", elapsed)?.apply()
        }
    }
    private fun clearPending() {
        pendingInMemory = null
        prefs?.edit()?.remove("body")?.remove("identity")?.remove("day")?.remove("created")?.remove("elapsed")?.apply()
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
        internal fun identity(user: UserInfo): String = (user.anonymousId ?: "") + "\u0000" + (user.id ?: "")
        internal fun pendingAge(now: Long, elapsed: Long, created: Long, createdElapsed: Long): Long? {
            val wallAge = now - created
            if (wallAge !in 0..MAX_AGE) return null
            if (createdElapsed <= 0 || elapsed < createdElapsed) return wallAge // reboot or no monotonic sample
            val monotonicAge = elapsed - createdElapsed
            if (kotlin.math.abs(monotonicAge - wallAge) > TimeUnit.MINUTES.toMillis(2)) return null
            return monotonicAge.takeIf { it <= MAX_AGE }
        }
        private fun day(now: Long): String = Calendar.getInstance().apply { timeInMillis = now }.let {
            "${it.get(Calendar.YEAR)}-${it.get(Calendar.DAY_OF_YEAR)}"
        }
        internal fun retryAfter(value: String?, now: Long): Long {
            value?.toLongOrNull()?.let { return now + TimeUnit.SECONDS.toMillis(it.coerceIn(0, 72 * 60 * 60)) }
            val date = try {
                SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("GMT")
                    isLenient = false
                }.parse(value ?: "")?.time
            } catch (e: java.text.ParseException) { null }
            return (date ?: (now + 60_000)).coerceIn(now, now + MAX_AGE)
        }
        internal fun buildEndpoint(intake: String, token: String): String {
            val url = intake.toHttpUrl()
            val forwarded = url.queryParameter("ddforward")
            if (forwarded != null) {
                val parts = forwarded.split('?', limit = 2)
                val path = parts[0].trimEnd('/') + "/active"
                val target = if (parts.size == 2) "$path?${parts[1]}" else path
                return url.newBuilder().setQueryParameter("ddforward", target)
                    .setQueryParameter("client_token", token).build().toString()
            }
            return url.newBuilder().encodedPath(url.encodedPath.trimEnd('/') + "/active")
                .setQueryParameter("client_token", token)
                .build().toString()
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
