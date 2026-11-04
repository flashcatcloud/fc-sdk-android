/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.internal.active

import android.content.Context
import android.content.SharedPreferences
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.context.DeviceInfo
import com.datadog.android.api.context.DeviceType
import com.datadog.android.api.context.UserInfo
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.privacy.TrackingConsent
import okhttp3.Call
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal class ActiveBeaconControllerTest {
    private val core: InternalSdkCore = mock()
    private val app: Context = mock()
    private val prefs: SharedPreferences = mock()
    private val editor: SharedPreferences.Editor = mock()
    private val context: DatadogContext = mock()
    private val device: DeviceInfo = mock()
    private val executor: ScheduledExecutorService = mock()
    private val factory: Call.Factory = mock()
    private val call: Call = mock()
    private lateinit var controller: ActiveBeaconController
    private val anonymousId = "3f2b9c1e-8a4d-4e5f-9b6a-1c2d3e4f5a6b"

    @BeforeEach
    fun setup() {
        whenever(core.name).thenReturn("default")
        whenever(core.internalLogger).thenReturn(mock())
        whenever(app.getSharedPreferences(any(), any())).thenReturn(prefs)
        whenever(prefs.edit()).thenReturn(editor)
        whenever(editor.clear()).thenReturn(editor)
        whenever(editor.commit()).thenReturn(true)
        whenever(editor.remove(any())).thenReturn(editor)
        whenever(editor.putString(any(), anyOrNull())).thenReturn(editor)
        whenever(editor.putLong(any(), any())).thenReturn(editor)
        whenever(context.clientToken).thenReturn("token")
        whenever(context.trackingConsent).thenReturn(TrackingConsent.GRANTED)
        whenever(context.userInfo).thenReturn(UserInfo())
        whenever(context.deviceInfo).thenReturn(device)
        whenever(context.service).thenReturn("service")
        whenever(context.env).thenReturn("prod")
        whenever(context.version).thenReturn("1.0")
        whenever(context.sdkVersion).thenReturn("2.0")
        whenever(device.osName).thenReturn("Android")
        whenever(device.osVersion).thenReturn("14")
        whenever(device.deviceType).thenReturn(DeviceType.MOBILE)
        whenever(device.deviceModel).thenReturn("model")
        whenever(device.deviceBrand).thenReturn("brand")
        whenever(executor.execute(any())).doAnswer { it.getArgument<Runnable>(0).run() }
        whenever(executor.schedule(any<Runnable>(), any(), any())).thenReturn(mock<ScheduledFuture<Any>>())
        whenever(factory.newCall(any())).thenReturn(call)
        whenever(call.execute()).thenReturn(response(202))
        controller = ActiveBeaconController(
            core, context, app, "app", "https://example.com/api/v2/rum",
            true, executor, factory, "process"
        )
    }

    @Test
    fun `waits for anonymous id and sends each user combination once`() {
        controller.onForeground(true)
        controller.onUserInfo(UserInfo(id = "u1"), TrackingConsent.GRANTED)
        verify(factory, never()).newCall(any())
        controller.onUserInfo(UserInfo(anonymousId, "u1"), TrackingConsent.GRANTED)
        controller.onUserInfo(UserInfo(anonymousId, "u1"), TrackingConsent.GRANTED)
        controller.onUserInfo(UserInfo(anonymousId, "u2"), TrackingConsent.GRANTED)
        verify(call, times(2)).execute()
    }

    @Test
    fun `consent and foreground gate requests`() {
        controller.onForeground(false)
        controller.onUserInfo(UserInfo(anonymousId, "u1"), TrackingConsent.GRANTED)
        controller.onConsent(TrackingConsent.NOT_GRANTED)
        controller.onForeground(true)
        verify(factory, never()).newCall(any())
        controller.onConsent(TrackingConsent.GRANTED)
        verify(call).execute()
        verify(editor).clear()
    }

    @Test
    fun `permanent errors do not retry while server errors do`() {
        whenever(call.execute()).thenReturn(response(400), response(503))
        controller.onForeground(true)
        controller.onUserInfo(UserInfo(anonymousId, "u1"), TrackingConsent.GRANTED)
        verify(executor).schedule(any<Runnable>(), any(), eq(TimeUnit.MILLISECONDS)) // Only midnight.
        controller.onUserInfo(UserInfo(anonymousId, "u2"), TrackingConsent.GRANTED)
        val delays = argumentCaptor<Long>()
        verify(executor, times(2)).schedule(any<Runnable>(), delays.capture(), eq(TimeUnit.MILLISECONDS))
        assertThat(delays.lastValue).isBetween(4_000L, 5_000L)
    }

    @Test
    fun `body follows backend schema and stays bounded with escaped input`() {
        whenever(context.service).thenReturn("\u0001".repeat(1000))
        val body = ActiveBeaconController.buildBody(context, UserInfo(anonymousId, "u1"))!!
        val json = JSONObject(body)
        assertThat(json.getJSONObject("usr").getString("anonymous_id")).isEqualTo(anonymousId)
        assertThat(json.getJSONObject("usr").getString("id")).isEqualTo("u1")
        assertThat(json.getJSONObject("device").getString("type")).isEqualTo("mobile")
        assertThat(body.toByteArray(Charsets.UTF_8).size).isLessThan(2016)
    }

    @Test
    fun `endpoint preserves string proxy and keeps the token out of the url`() {
        assertThat(ActiveBeaconController.buildEndpoint("https://example.com/api/v2/rum/"))
            .isEqualTo("https://example.com/api/v2/rum/active")
        val url = ActiveBeaconController.buildEndpoint("https://example.com/proxy?ddforward=%2Fapi%2Fv2%2Frum")
        assertThat(url).isEqualTo("https://example.com/proxy?ddforward=%2Fapi%2Fv2%2Frum%2Factive")
    }

    @Test
    fun `client token is sent in the api key header`() {
        controller.onForeground(true)
        controller.onUserInfo(UserInfo(anonymousId, "u1"), TrackingConsent.GRANTED)
        val request = argumentCaptor<Request>()
        verify(factory).newCall(request.capture())
        assertThat(request.lastValue.header("DD-API-KEY")).isEqualTo("token")
        assertThat(request.lastValue.url.queryParameter("client_token")).isNull()
    }

    @Test
    fun `repeated withheld consent clears storage once unless the deletion failed`() {
        controller.onUserInfo(UserInfo(anonymousId, "u1"), TrackingConsent.NOT_GRANTED)
        controller.onUserInfo(UserInfo(anonymousId, "u2"), TrackingConsent.NOT_GRANTED)
        controller.onConsent(TrackingConsent.NOT_GRANTED)
        verify(editor, times(1)).commit()

        whenever(editor.commit()).thenReturn(false)
        controller.clearData()
        controller.onUserInfo(UserInfo(anonymousId, "u3"), TrackingConsent.NOT_GRANTED)
        verify(editor, times(3)).commit()
    }

    @Test
    fun `rate limit honors backend delay and schedules bounded retry`() {
        whenever(call.execute()).thenReturn(response(429, "Flashcat-Request-Rate-Limit-Delay" to "7"))
        controller.onForeground(true)
        controller.onUserInfo(UserInfo(anonymousId, "u1"), TrackingConsent.GRANTED)
        verify(editor).putLong(eq("next_allowed"), any())
        val delays = argumentCaptor<Long>()
        verify(executor, times(2)).schedule(any<Runnable>(), delays.capture(), eq(TimeUnit.MILLISECONDS))
        assertThat(delays.lastValue).isBetween(6_000L, 7_000L)
    }

    @Test
    fun `retry delay accepts both headers and clamps zero`() {
        assertThat(ActiveBeaconController.retryAfter("0", "20", 1_000)).isEqualTo(2_000)
        assertThat(ActiveBeaconController.retryAfter(null, "15", 1_000)).isEqualTo(16_000)
        assertThat(ActiveBeaconController.retryAfter("-1", null, 1_000)).isEqualTo(61_000)
    }

    @Test
    fun `pending age uses monotonic clock and discards suspicious wall clock changes`() {
        assertThat(ActiveBeaconController.pendingAge(12_000, 7_000, 10_000, 5_000, "a", "a")).isEqualTo(2_000)
        assertThat(ActiveBeaconController.pendingAge(500_000, 7_000, 10_000, 5_000, "a", "a")).isEqualTo(2_000)
        assertThat(ActiveBeaconController.pendingAge(12_000, 7_000, 10_000, 5_000, "b", "a")).isEqualTo(2_000)
        assertThat(ActiveBeaconController.pendingAge(9_000, 7_000, 10_000, 5_000, "b", "a")).isNull()
        assertThat(ActiveBeaconController.pendingAge(500_000, 7_000, 10_000, 5_000, null, null)).isNull()
    }

    @Test
    fun `network failure is retried twice and then accepted`() {
        val retries = mutableListOf<Runnable>()
        whenever(executor.schedule(any<Runnable>(), any(), any())).doAnswer {
            if (it.getArgument<Long>(1) in 4_000L..60_000L) retries.add(it.getArgument(0))
            mock<ScheduledFuture<Any>>()
        }
        whenever(call.execute()).thenReturn(response(503), response(503), response(202))
        controller.onForeground(true)
        controller.onUserInfo(UserInfo(anonymousId, "u1"), TrackingConsent.GRANTED)
        retries.removeAt(0).run()
        retries.removeAt(0).run()
        verify(call, times(3)).execute()
    }

    @Test
    fun `queued foreground notification is kept but not sent after background transition`() {
        val entries = memoryPreferences()
        val queued = mutableListOf<Runnable>()
        whenever(executor.execute(any())).doAnswer {
            queued.add(it.getArgument(0))
            Unit
        }
        controller.onForeground(false)
        controller.onUserInfo(UserInfo(anonymousId, "u1"), TrackingConsent.GRANTED)
        controller.onForeground(true)
        controller.onForeground(false)
        queued.toList().forEach { it.run() }
        verify(factory, never()).newCall(any())
        assertThat(JSONObject(entries["body"] as String).getJSONObject("usr").getString("id")).isEqualTo("u1")
    }

    @Test
    fun `account switched while an old request blocks the worker is kept after backgrounding`() {
        val entries = memoryPreferences()
        var now = System.currentTimeMillis()
        val switchedAt = now + 1_000
        val queued = ArrayDeque<Runnable>()
        whenever(executor.execute(any())).doAnswer {
            queued.add(it.getArgument(0))
            Unit
        }
        val actual = ActiveBeaconController(
            core, context, app, "app", "https://example.com/api/v2/rum", true, executor, factory, "process",
            wallClock = { now }
        )
        whenever(call.execute()).doAnswer {
            now = switchedAt
            actual.onUserInfo(UserInfo(anonymousId, "B"), TrackingConsent.GRANTED)
            actual.onForeground(false)
            now += 10_000
            response(202)
        }

        actual.onForeground(true)
        actual.onUserInfo(UserInfo(anonymousId, "A"), TrackingConsent.GRANTED)
        while (queued.isNotEmpty()) queued.removeFirst().run()

        val requests = argumentCaptor<Request>()
        verify(factory).newCall(requests.capture())
        assertThat(requestUser(requests.lastValue)).isEqualTo("A")
        assertThat(JSONObject(entries["body"] as String).getJSONObject("usr").getString("id")).isEqualTo("B")
        assertThat(entries).containsEntry("created", switchedAt)

        whenever(call.execute()).thenReturn(response(202))
        actual.onForeground(true)
        while (queued.isNotEmpty()) queued.removeFirst().run()
        verify(factory, times(2)).newCall(requests.capture())
        assertThat(requestUser(requests.lastValue)).isEqualTo("B")
        assertThat(entries).doesNotContainKey("body")
    }

    @Test
    fun `foreground notification does not wait for a lock held during storage work`() {
        val queued = mutableListOf<Runnable>()
        whenever(executor.execute(any())).doAnswer {
            queued.add(it.getArgument(0))
            Unit
        }
        val stateLock = ActiveBeaconController::class.java.getDeclaredField("lock").apply {
            isAccessible = true
        }.get(controller)
        val holding = CountDownLatch(1)
        val release = CountDownLatch(1)
        val holder = Thread {
            synchronized(stateLock) {
                holding.countDown()
                release.await(5, TimeUnit.SECONDS)
            }
        }
        holder.start()
        try {
            assertThat(holding.await(5, TimeUnit.SECONDS)).isTrue()
            val notified = CountDownLatch(1)
            Thread {
                controller.onForeground(true)
                notified.countDown()
            }.start()
            assertThat(notified.await(1, TimeUnit.SECONDS)).isTrue()
            assertThat(queued).hasSize(1)
        } finally {
            release.countDown()
            holder.join(5_000)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["pending", "withdrawn", "regranted", "clear", "pendingAtLogin"])
    fun `queued account switch is dropped when invalidated before the worker resumes`(invalidation: String) {
        val entries = memoryPreferences()
        val queued = ArrayDeque<Runnable>()
        whenever(executor.execute(any())).doAnswer {
            queued.add(it.getArgument(0))
            Unit
        }
        whenever(call.execute()).doAnswer {
            val loginConsent = if (invalidation == "pendingAtLogin") TrackingConsent.PENDING else TrackingConsent.GRANTED
            controller.onUserInfo(UserInfo(anonymousId, "B"), loginConsent)
            controller.onForeground(false)
            when (invalidation) {
                "pending" -> controller.onConsent(TrackingConsent.PENDING)
                "withdrawn" -> controller.onConsent(TrackingConsent.NOT_GRANTED)
                "regranted" -> {
                    controller.onConsent(TrackingConsent.NOT_GRANTED)
                    controller.onConsent(TrackingConsent.GRANTED)
                }
                "clear" -> controller.clearData()
                "pendingAtLogin" -> controller.onConsent(TrackingConsent.GRANTED)
            }
            response(202)
        }

        controller.onForeground(true)
        controller.onUserInfo(UserInfo(anonymousId, "A"), TrackingConsent.GRANTED)
        while (queued.isNotEmpty()) queued.removeFirst().run()

        verify(call).execute()
        assertThat(entries).doesNotContainKey("body")
    }

    @Test
    fun `older pending beacon is sent with its age before current identity`() {
        val old = ActiveBeaconController.buildBody(context, UserInfo(anonymousId, "old"))!!
        whenever(prefs.getString(eq("body"), anyOrNull())).thenReturn(old)
        whenever(prefs.getString(eq("identity"), any())).thenReturn("old-key")
        whenever(prefs.getString(eq("day"), any())).thenReturn("old-day")
        whenever(prefs.getLong(eq("created"), any())).thenReturn(System.currentTimeMillis() - 3_000)
        controller.onForeground(true)
        controller.onUserInfo(UserInfo(anonymousId, "new"), TrackingConsent.GRANTED)
        val requests = argumentCaptor<Request>()
        verify(factory, times(2)).newCall(requests.capture())
        val buffer = okio.Buffer()
        requests.firstValue.body!!.writeTo(buffer)
        val body = JSONObject(buffer.readUtf8())
        assertThat(body.getLong("age_ms")).isGreaterThanOrEqualTo(3000)
        assertThat(body.getJSONObject("usr").getString("id")).isEqualTo("old")
    }

    @Test
    fun `pending consent preserves existing record until granted`() {
        val value = UserInfo(anonymousId, "u1")
        val old = ActiveBeaconController.buildBody(context, value)!!
        val cal = Calendar.getInstance()
        whenever(prefs.getString(eq("body"), anyOrNull())).thenReturn(old)
        whenever(prefs.getString(eq("identity"), any())).thenReturn(ActiveBeaconController.identity(value))
        whenever(
            prefs.getString(eq("day"), any())
        ).thenReturn("${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}")
        whenever(prefs.getLong(eq("created"), any())).thenReturn(System.currentTimeMillis() - 3_000)
        controller.onForeground(true)
        controller.onUserInfo(value, TrackingConsent.PENDING)
        verify(editor, never()).clear()
        verify(factory, never()).newCall(any())
        controller.onConsent(TrackingConsent.GRANTED)
        val request = argumentCaptor<Request>()
        verify(factory).newCall(request.capture())
        val buffer = okio.Buffer()
        request.firstValue.body!!.writeTo(buffer)
        assertThat(JSONObject(buffer.readUtf8()).getLong("age_ms")).isGreaterThanOrEqualTo(3_000)
    }

    @Test
    fun `failed beacon survives restart with pending consent and is delivered after grant`() {
        val entries = memoryPreferences()
        whenever(call.execute()).thenReturn(response(503), response(202))
        val user = UserInfo(anonymousId, "u1")
        controller.onForeground(true)
        controller.onUserInfo(user, TrackingConsent.GRANTED)
        assertThat(entries).containsKey("body")
        controller.stop()

        val restarted = ActiveBeaconController(
            core, context, app, "app", "https://example.com/api/v2/rum", true, executor, factory, "process"
        )
        restarted.onForeground(true)
        entries["next_allowed"] = 0L
        restarted.onUserInfo(user, TrackingConsent.PENDING)
        assertThat(entries).containsKey("body")
        assertThat(entries).containsEntry("next_allowed", 0L)
        verify(call, times(1)).execute()
        restarted.onConsent(TrackingConsent.GRANTED)
        verify(call, times(2)).execute()
        assertThat(entries).doesNotContainKey("body")
    }

    @Test
    fun `already sent user cannot replace a rate limited pending user`() {
        whenever(call.execute()).thenReturn(response(202), response(429))
        controller.onForeground(true)
        controller.onUserInfo(UserInfo(anonymousId, "u1"), TrackingConsent.GRANTED)
        controller.onUserInfo(UserInfo(anonymousId, "u2"), TrackingConsent.GRANTED)
        controller.onUserInfo(UserInfo(anonymousId, "u1"), TrackingConsent.GRANTED)
        verify(
            editor,
            times(1)
        ).putString(eq("identity"), eq(ActiveBeaconController.identity(UserInfo(anonymousId, "u2"))))
        verify(
            editor,
            never()
        ).putString(eq("identity"), eq(ActiveBeaconController.identity(UserInfo(anonymousId, "u1"))))
    }

    @Test
    fun `failed pending user is retained when current user was already sent`() {
        whenever(call.execute()).thenReturn(response(202), response(503))
        controller.onForeground(true)
        val current = UserInfo(anonymousId, "u1")
        controller.onUserInfo(current, TrackingConsent.GRANTED)
        val pendingBody = ActiveBeaconController.buildBody(context, UserInfo(anonymousId, "u2"))!!
        whenever(prefs.getString(eq("body"), anyOrNull())).thenReturn(pendingBody)
        whenever(
            prefs.getString(eq("identity"), any())
        ).thenReturn(ActiveBeaconController.identity(UserInfo(anonymousId, "u2")))
        whenever(prefs.getString(eq("day"), any())).thenReturn("previous-day")
        whenever(prefs.getLong(eq("created"), any())).thenReturn(System.currentTimeMillis() - 3_000)
        controller.onForeground(true)
        verify(call, times(2)).execute()
        verify(editor, never()).putString(eq("identity"), any())
    }

    @Test
    fun `restored rate limit schedules a retry without replacing the same pending identity`() {
        val pendingBody = ActiveBeaconController.buildBody(context, UserInfo(anonymousId, "u1"))!!
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        val today = "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.DAY_OF_YEAR)}"
        whenever(prefs.getLong(eq("next_allowed"), any())).thenReturn(now + 60_000)
        whenever(prefs.getString(eq("body"), anyOrNull())).thenReturn(pendingBody)
        whenever(prefs.getString(eq("identity"), any())).thenReturn(
            ActiveBeaconController.identity(UserInfo(anonymousId, "u1"))
        )
        whenever(prefs.getString(eq("day"), any())).thenReturn(today)
        whenever(prefs.getLong(eq("created"), any())).thenReturn(now - 3_000)
        controller.onForeground(true)
        controller.onUserInfo(UserInfo(anonymousId, "u1"), TrackingConsent.GRANTED)
        controller.onForeground(true)
        verify(call, never()).execute()
        verify(editor, never()).putString(eq("body"), any())
        val delays = argumentCaptor<Long>()
        verify(executor, times(2)).schedule(any<Runnable>(), delays.capture(), eq(TimeUnit.MILLISECONDS))
        assertThat(delays.allValues.count { it in 1_000..60_000 }).isEqualTo(1)
    }

    @Test
    fun `consent withdrawal clears the in memory rate limit`() {
        whenever(call.execute()).thenReturn(response(429), response(202))
        controller.onForeground(true)
        controller.onUserInfo(UserInfo(anonymousId, "u1"), TrackingConsent.GRANTED)
        controller.onConsent(TrackingConsent.NOT_GRANTED)
        controller.onConsent(TrackingConsent.GRANTED)
        verify(call, times(2)).execute()
    }

    @Test
    fun `http timeout is retried`() {
        whenever(call.execute()).thenReturn(response(408))
        controller.onForeground(true)
        controller.onUserInfo(UserInfo(anonymousId, "u1"), TrackingConsent.GRANTED)
        val delays = argumentCaptor<Long>()
        verify(executor, times(2)).schedule(any<Runnable>(), delays.capture(), eq(TimeUnit.MILLISECONDS))
        assertThat(delays.lastValue).isBetween(4_000L, 5_000L)
    }

    @ParameterizedTest
    @ValueSource(strings = ["edit", "commit", "false"])
    fun `failed deletion never replays old storage and can be retried`(failure: String) {
        val old = UserInfo(anonymousId, "old")
        val oldBody = ActiveBeaconController.buildBody(context, old)
        whenever(prefs.getString(eq("body"), anyOrNull())).thenReturn(oldBody)
        whenever(prefs.getLong(eq("created"), any())).thenReturn(System.currentTimeMillis() - 3_000)
        when (failure) {
            "edit" -> whenever(prefs.edit()).thenThrow(IllegalStateException("storage"))
            "commit" -> whenever(editor.commit()).thenThrow(IllegalStateException("storage"))
            else -> whenever(editor.commit()).thenReturn(false)
        }

        controller.clearData()
        controller.onForeground(true)
        controller.onUserInfo(UserInfo(anonymousId, "new"), TrackingConsent.GRANTED)

        val requests = argumentCaptor<Request>()
        verify(factory).newCall(requests.capture())
        val body = okio.Buffer().also { requests.firstValue.body!!.writeTo(it) }.readUtf8()
        assertThat(JSONObject(body).getJSONObject("usr").getString("id")).isEqualTo("new")
        verify(prefs, never()).getString(eq("body"), anyOrNull())

        // A later explicit clear retries disk deletion even after storage has been disabled.
        org.mockito.kotlin.doReturn(editor).whenever(prefs).edit()
        org.mockito.kotlin.doReturn(true).whenever(editor).commit()
        controller.clearData()
        verify(prefs, times(2)).edit()
        verify(editor, times(if (failure == "edit") 1 else 2)).commit()
    }

    @Test
    fun `foreground churn retains only one midnight task in the real scheduler`() {
        val scheduler = ScheduledThreadPoolExecutor(1)
        val actual = ActiveBeaconController(
            core, context, app, "app", "https://example.com/api/v2/rum", true, scheduler, factory, "process"
        )
        try {
            actual.onForeground(true)
            repeat(1_000) {
                actual.onForeground(false)
                actual.onForeground(true)
            }
            scheduler.submit {}.get(5, TimeUnit.SECONDS)
            assertThat(scheduler.queue).hasSize(1)
            actual.stop()
            assertThat(scheduler.queue).isEmpty()
        } finally {
            scheduler.shutdownNow()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["timezone", "clock"])
    fun `foreground recomputes changed midnight without retaining canceled tasks`(change: String) {
        val originalZone = TimeZone.getDefault()
        val scheduler = ScheduledThreadPoolExecutor(1)
        var now = 1_767_261_600_000L // 2026-01-01 10:00 UTC
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val actual = ActiveBeaconController(
            core, context, app, "app", "https://example.com/api/v2/rum", true, scheduler, factory, "process",
            wallClock = { now }
        )
        try {
            actual.onForeground(true)
            scheduler.submit {}.get(5, TimeUnit.SECONDS)
            val original = scheduler.queue.single() as ScheduledFuture<*>
            assertThat(original.getDelay(TimeUnit.SECONDS)).isBetween(14 * 3600L - 5, 14 * 3600L)

            actual.onForeground(false)
            scheduler.submit {}.get(5, TimeUnit.SECONDS)
            if (change == "timezone") {
                TimeZone.setDefault(TimeZone.getTimeZone("GMT+08:00"))
            } else {
                now += TimeUnit.HOURS.toMillis(8)
            }
            actual.onForeground(true)
            scheduler.submit {}.get(5, TimeUnit.SECONDS)

            assertThat(original.isCancelled).isTrue()
            assertThat(scheduler.queue).hasSize(1)
            val updated = scheduler.queue.single() as ScheduledFuture<*>
            assertThat(updated.getDelay(TimeUnit.SECONDS)).isBetween(6 * 3600L - 5, 6 * 3600L)
            repeat(1_000) {
                actual.onForeground(false)
                actual.onForeground(true)
            }
            scheduler.submit {}.get(5, TimeUnit.SECONDS)
            assertThat(scheduler.queue).containsExactly(updated as Runnable)
            actual.stop()
            assertThat(scheduler.queue).isEmpty()
        } finally {
            scheduler.shutdownNow()
            TimeZone.setDefault(originalZone)
        }
    }

    @Test
    fun `midnight task rearms after firing and not while stopped`() {
        controller.onForeground(true)
        val tasks = argumentCaptor<Runnable>()
        verify(executor).schedule(tasks.capture(), any(), eq(TimeUnit.MILLISECONDS))
        tasks.firstValue.run()
        verify(executor, times(2)).schedule(tasks.capture(), any(), eq(TimeUnit.MILLISECONDS))
        controller.stop()
        tasks.lastValue.run()
        verify(executor, times(2)).schedule(any<Runnable>(), any(), eq(TimeUnit.MILLISECONDS))
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `invalidation between reading pending and creating call discards the old work`(revoke: Boolean) {
        val old = UserInfo(anonymousId, "old")
        val oldBody = ActiveBeaconController.buildBody(context, old)
        whenever(prefs.getString(eq("body"), anyOrNull())).thenReturn(oldBody)
        whenever(prefs.getString(eq("identity"), any())).thenReturn(ActiveBeaconController.identity(old))
        whenever(prefs.getLong(eq("created"), any())).thenReturn(System.currentTimeMillis() - 3_000)
        whenever(editor.commit()).doAnswer {
            whenever(prefs.getString(eq("body"), anyOrNull())).thenReturn(null)
            true
        }
        controller.onForeground(false)
        controller.onUserInfo(UserInfo(anonymousId, "current"), TrackingConsent.GRANTED)
        val queued = ArrayDeque<Runnable>()
        whenever(executor.execute(any())).doAnswer { queued.add(it.getArgument(0)); Unit }
        whenever(prefs.getString(eq("day"), any())).doAnswer {
            // All old payload fields have already been read, but no Call exists to cancel yet.
            if (revoke) {
                controller.onConsent(TrackingConsent.NOT_GRANTED)
                controller.onConsent(TrackingConsent.GRANTED)
            } else {
                controller.clearData()
            }
            "previous-day"
        }

        controller.onForeground(true)
        queued.removeFirst().run()
        verify(factory, never()).newCall(any())
        verify(editor, never()).putString(eq("body"), anyOrNull())
        while (queued.isNotEmpty()) queued.removeFirst().run()
        controller.onForeground(true)
        while (queued.isNotEmpty()) queued.removeFirst().run()

        val requests = argumentCaptor<Request>()
        verify(factory).newCall(requests.capture())
        val body = okio.Buffer().also { requests.firstValue.body!!.writeTo(it) }.readUtf8()
        assertThat(JSONObject(body).getJSONObject("usr").getString("id")).isEqualTo("current")
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `old retry cannot claim a new record after clear or withdrawal`(revoke: Boolean) {
        val retries = ControlledRetries()
        whenever(call.execute()).thenAnswer { response(503) }
        retries.controller.onForeground(true)
        retries.controller.onUserInfo(UserInfo(anonymousId, "old"), TrackingConsent.GRANTED)
        val stale = retries.active.single()
        retries.controller.onForeground(false)
        if (revoke) retries.controller.onConsent(TrackingConsent.NOT_GRANTED) else retries.controller.clearData()
        retries.controller.onUserInfo(UserInfo(anonymousId, "new"), TrackingConsent.GRANTED)
        retries.controller.onForeground(true)
        val taskCount = retries.tasks.size

        stale.runnable.run() // Deliberately invoke the canceled callback, as if already dequeued.

        verify(call, times(2)).execute()
        assertThat(retries.tasks).hasSize(taskCount)
        assertThat(retries.active).hasSize(1)
        retries.runNext()
        retries.runNext()
        assertThat(retries.active).isEmpty()
        val requests = argumentCaptor<Request>()
        verify(factory, times(4)).newCall(requests.capture())
        assertThat(requests.allValues.map(::requestUser)).containsExactly("old", "new", "new", "new")
    }

    @Test
    fun `replacing pending cancels old rounds and gives the replacement its own retry budget`() {
        val retries = ControlledRetries()
        whenever(call.execute()).thenAnswer { response(503) }
        retries.controller.onForeground(true)
        retries.controller.onUserInfo(UserInfo(anonymousId, "old"), TrackingConsent.GRANTED)
        retries.controller.onUserInfo(UserInfo(anonymousId, "new"), TrackingConsent.GRANTED)
        val stale = retries.tasks.filter { it.cancelled }
        assertThat(stale).hasSize(2)
        val taskCount = retries.tasks.size
        stale.forEach { it.runnable.run() }
        verify(call, times(2)).execute()
        assertThat(retries.tasks).hasSize(taskCount)

        repeat(3) {
            assertThat(retries.active).hasSize(1)
            retries.runNext()
        }

        assertThat(retries.active).isEmpty()
        val requests = argumentCaptor<Request>()
        verify(factory, times(5)).newCall(requests.capture())
        assertThat(requests.allValues.map(::requestUser)).containsExactly("old", "old", "new", "new", "new")
    }

    @Test
    fun `new foreground round invalidates every older retry`() {
        val retries = ControlledRetries()
        whenever(call.execute()).thenAnswer { response(503) }
        retries.controller.onForeground(true)
        retries.controller.onUserInfo(UserInfo(anonymousId, "user"), TrackingConsent.GRANTED)
        repeat(10) {
            retries.controller.onForeground(false)
            retries.controller.onForeground(true)
            assertThat(retries.active).hasSize(1)
        }
        val taskCount = retries.tasks.size
        retries.tasks.filter { it.cancelled }.forEach { it.runnable.run() }
        verify(call, times(11)).execute()
        assertThat(retries.tasks).hasSize(taskCount)
        retries.runNext()
        retries.runNext()
        verify(call, times(13)).execute()
        assertThat(retries.active).isEmpty()
    }

    @Test
    fun `network and rate limit share one bounded round including an early limit callback`() {
        val retries = ControlledRetries()
        whenever(call.execute()).thenReturn(response(503), response(429, "Retry-After" to "7"), response(503))
        retries.controller.onForeground(true)
        retries.controller.onUserInfo(UserInfo(anonymousId, "user"), TrackingConsent.GRANTED)
        val first = retries.active.single()
        retries.runNext()
        val limited = retries.active.single()
        assertThat(limited.deadline - retries.now).isEqualTo(7_000)
        first.runnable.run()
        limited.ran = true
        limited.runnable.run() // Deadline not reached: reschedule the same attempt, not a new round.
        limited.runnable.run() // A consumed callback is inert as well.
        verify(call, times(2)).execute()
        assertThat(retries.active).hasSize(1)
        assertThat(retries.active.single().deadline).isEqualTo(limited.deadline)
        retries.runNext()
        verify(call, times(3)).execute()
        assertThat(retries.active).isEmpty()
    }

    @Test
    fun `invalidated rate limit response cannot delay a trigger queued before clear reset`() {
        val retries = ControlledRetries()
        val queued = ArrayDeque<Runnable>()
        whenever(executor.execute(any())).doAnswer { queued.add(it.getArgument(0)); Unit }
        var calls = 0
        whenever(call.execute()).doAnswer {
            if (calls++ == 0) {
                // Worker is inside the old HTTP call. A new identity is queued before clear's
                // worker reset, while clear synchronously invalidates this in-flight generation.
                retries.controller.onUserInfo(UserInfo(anonymousId, "new"), TrackingConsent.GRANTED)
                retries.controller.clearData()
                response(429, "Retry-After" to "3600")
            } else {
                response(202)
            }
        }
        retries.controller.onForeground(true)
        retries.controller.onUserInfo(UserInfo(anonymousId, "old"), TrackingConsent.GRANTED)
        queued.removeFirst().run() // start, before the worker receives user info
        queued.removeFirst().run() // old HTTP request; queues new user, then clear reset
        assertThat(queued).hasSize(2)
        verify(call).execute()

        queued.removeFirst().run() // new user must not see the invalidated 429's one-hour delay

        val requests = argumentCaptor<Request>()
        verify(factory, times(2)).newCall(requests.capture())
        assertThat(requests.allValues.map(::requestUser)).containsExactly("old", "new")
        verify(editor, never()).putLong(eq("next_allowed"), any())
        verify(executor).schedule(any<Runnable>(), any(), eq(TimeUnit.MILLISECONDS)) // midnight only
        queued.removeFirst().run()
        assertThat(queued).isEmpty()
        assertThat(retries.active).isEmpty()
    }

    @Test
    fun `background transition before call admission persists the beacon for the next process`() {
        val entries = memoryPreferences()
        val scheduler = ScheduledThreadPoolExecutor(1)
        val actual = ActiveBeaconController(
            core, context, app, "app", "https://example.com/api/v2/rum", true, scheduler, factory, "process"
        )
        val readingPending = CountDownLatch(1)
        val continueReading = CountDownLatch(1)
        val worker = AtomicReference<Thread>()
        try {
            actual.onForeground(false)
            actual.onUserInfo(UserInfo(anonymousId, "user"), TrackingConsent.GRANTED)
            scheduler.submit {}.get(5, TimeUnit.SECONDS)
            whenever(prefs.getString(eq("body"), anyOrNull())).doAnswer {
                worker.set(Thread.currentThread())
                readingPending.countDown()
                check(continueReading.await(5, TimeUnit.SECONDS))
                entries["body"] as? String
            }
            actual.onForeground(true)
            assertThat(readingPending.await(5, TimeUnit.SECONDS)).isTrue()
            val stateLock = ActiveBeaconController::class.java.getDeclaredField("lock").apply {
                isAccessible = true
            }.get(actual)
            synchronized(stateLock) {
                continueReading.countDown()
                // The worker passed send's first eligible check and is waiting to create the Call.
                val timeout = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                while (worker.get().state != Thread.State.BLOCKED && System.nanoTime() < timeout) {
                    Thread.yield()
                }
                assertThat(worker.get().state).isEqualTo(Thread.State.BLOCKED)
                val blocked = worker.get().stackTrace.first()
                assertThat(blocked.className).isEqualTo(ActiveBeaconController::class.java.name)
                assertThat(blocked.methodName).isEqualTo("send")
                verify(factory, never()).newCall(any())
                actual.onForeground(false)
            }
            scheduler.submit {}.get(5, TimeUnit.SECONDS)
            verify(factory, never()).newCall(any())

            assertThat(JSONObject(entries["body"] as String).getJSONObject("usr").getString("id"))
                .isEqualTo("user")
            actual.stop()
            val restarted = ActiveBeaconController(
                core, context, app, "app", "https://example.com/api/v2/rum", true, executor, factory, "process"
            )
            restarted.onForeground(false)
            restarted.onUserInfo(UserInfo(anonymousId, "user"), TrackingConsent.GRANTED)
            verify(factory, never()).newCall(any())
            restarted.onForeground(true)
            verify(call).execute()
            assertThat(entries).doesNotContainKey("body")
        } finally {
            continueReading.countDown()
            actual.stop()
        }
    }

    @Test
    fun `request admitted in foreground may complete after background transition`() {
        whenever(call.execute()).doAnswer {
            controller.onForeground(false)
            response(202)
        }
        controller.onForeground(true)
        controller.onUserInfo(UserInfo(anonymousId, "user"), TrackingConsent.GRANTED)
        controller.onForeground(true)
        verify(call).execute()
        verify(call, never()).cancel()
    }

    @ParameterizedTest
    @ValueSource(ints = [202, 503])
    fun `backgrounding while replay is in flight preserves the current foreground snapshot`(oldStatus: Int) {
        val entries = memoryPreferences()
        var now = System.currentTimeMillis()
        val startedAt = now
        val current = UserInfo(anonymousId, "new")
        entries["body"] = ActiveBeaconController.buildBody(context, UserInfo(anonymousId, "old"))!!
        entries["identity"] = "old-key"
        entries["day"] = "previous-day"
        entries["created"] = now - 3_000
        val actual = ActiveBeaconController(
            core, context, app, "app", "https://example.com/api/v2/rum", true, executor, factory, "process",
            wallClock = { now }
        )
        whenever(call.execute()).doAnswer {
            actual.onForeground(false)
            now += 10_000 // Time spent waiting for the old beacon must not change the new activity's age.
            response(oldStatus)
        }

        actual.onForeground(true)
        actual.onUserInfo(current, TrackingConsent.GRANTED)

        verify(call).execute() // Only the old replay was admitted before backgrounding.
        assertThat(JSONObject(entries["body"] as String).getJSONObject("usr").getString("id")).isEqualTo("new")
        assertThat(entries).containsEntry("created", startedAt)
        verify(executor).schedule(any<Runnable>(), any(), any()) // Initial midnight task only; no background retry.
        actual.stop()

        whenever(call.execute()).thenReturn(response(202))
        val restarted = ActiveBeaconController(
            core, context, app, "app", "https://example.com/api/v2/rum", true, executor, factory, "process",
            wallClock = { now }
        )
        restarted.onForeground(false)
        restarted.onUserInfo(current, TrackingConsent.GRANTED)
        verify(call).execute()
        restarted.onForeground(true)
        val requests = argumentCaptor<Request>()
        verify(factory, times(2)).newCall(requests.capture())
        assertThat(requests.allValues.map(::requestUser)).containsExactly("old", "new")
        val replay = JSONObject(okio.Buffer().also { requests.lastValue.body!!.writeTo(it) }.readUtf8())
        assertThat(replay.getLong("age_ms")).isGreaterThan(0)
        assertThat(entries).doesNotContainKey("body")
    }

    @ParameterizedTest
    @ValueSource(strings = ["pending", "withdrawn", "regranted", "clear", "stop"])
    fun `background deferral cannot restore a snapshot invalidated during replay`(invalidation: String) {
        val entries = memoryPreferences()
        entries["body"] = ActiveBeaconController.buildBody(context, UserInfo(anonymousId, "old"))!!
        entries["identity"] = "old-key"
        entries["day"] = "previous-day"
        entries["created"] = System.currentTimeMillis() - 3_000
        whenever(call.execute()).doAnswer {
            controller.onForeground(false)
            when (invalidation) {
                "pending" -> controller.onConsent(TrackingConsent.PENDING)
                "withdrawn" -> controller.onConsent(TrackingConsent.NOT_GRANTED)
                "regranted" -> {
                    controller.onConsent(TrackingConsent.NOT_GRANTED)
                    controller.onConsent(TrackingConsent.GRANTED)
                }
                "clear" -> controller.clearData()
                "stop" -> controller.stop()
            }
            response(503)
        }

        controller.onForeground(true)
        controller.onUserInfo(UserInfo(anonymousId, "new"), TrackingConsent.GRANTED)

        verify(call).execute()
        verify(editor, never()).putString(eq("body"), anyOrNull())
        if (invalidation == "pending" || invalidation == "stop") {
            assertThat(JSONObject(entries["body"] as String).getJSONObject("usr").getString("id")).isEqualTo("old")
        } else {
            assertThat(entries).doesNotContainKey("body")
        }
    }

    private fun memoryPreferences(): MutableMap<String, Any> {
        val entries = mutableMapOf<String, Any>()
        whenever(prefs.getString(any(), anyOrNull())).doAnswer {
            entries[it.getArgument<String>(0)] as? String ?: it.getArgument<String?>(1)
        }
        whenever(prefs.getLong(any(), any())).doAnswer {
            entries[it.getArgument<String>(0)] as? Long ?: it.getArgument<Long>(1)
        }
        whenever(editor.putString(any(), anyOrNull())).doAnswer {
            val key = it.getArgument<String>(0)
            val value = it.getArgument<String?>(1)
            if (value == null) entries.remove(key) else entries[key] = value
            editor
        }
        whenever(editor.putLong(any(), any())).doAnswer {
            entries[it.getArgument<String>(0)] = it.getArgument<Long>(1)
            editor
        }
        whenever(editor.remove(any())).doAnswer {
            entries.remove(it.getArgument<String>(0))
            editor
        }
        whenever(editor.clear()).doAnswer {
            entries.clear()
            editor
        }
        return entries
    }

    private fun requestUser(request: Request): String =
        JSONObject(okio.Buffer().also { request.body!!.writeTo(it) }.readUtf8()).getJSONObject("usr").getString("id")

    private class RetryTask(val runnable: Runnable, val deadline: Long) {
        var cancelled = false
        var ran = false
    }

    private inner class ControlledRetries {
        var now = java.time.Instant.parse("2026-01-01T10:00:00Z").toEpochMilli()
        val tasks = mutableListOf<RetryTask>()
        val active: List<RetryTask> get() = tasks.filter { !it.cancelled && !it.ran }
        val controller: ActiveBeaconController

        init {
            whenever(executor.schedule(any<Runnable>(), any(), any())).doAnswer {
                val task = RetryTask(it.getArgument(0), now + it.getArgument<Long>(1))
                val future = mock<ScheduledFuture<Any>>()
                whenever(future.cancel(any())).doAnswer { task.cancelled = true; true }
                if (it.getArgument<Long>(1) <= 60_000) tasks.add(task)
                future
            }
            controller = ActiveBeaconController(
                core, context, app, "app", "https://example.com/api/v2/rum", true, executor, factory, "process",
                wallClock = { now }
            )
        }

        fun runNext() {
            val task = active.single()
            now = maxOf(now, task.deadline)
            task.ran = true
            task.runnable.run()
        }
    }

    private fun response(code: Int, header: Pair<String, String>? = null): Response = Response.Builder().request(
        Request.Builder().url("https://example.com").build()
    )
        .apply { if (header != null) header(header.first, header.second) }
        .protocol(Protocol.HTTP_1_1).code(code).message("test").body("".toResponseBody()).build()
}
