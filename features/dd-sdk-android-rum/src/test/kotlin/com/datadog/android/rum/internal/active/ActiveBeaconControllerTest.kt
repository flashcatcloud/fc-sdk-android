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
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

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
        whenever(editor.remove(any())).thenReturn(editor)
        whenever(editor.putString(any(), any())).thenReturn(editor)
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
        controller.start(true)
        controller.onUserInfo(UserInfo(id = "u1"), TrackingConsent.GRANTED)
        verify(factory, never()).newCall(any())
        controller.onUserInfo(UserInfo(anonymousId, "u1"), TrackingConsent.GRANTED)
        controller.onUserInfo(UserInfo(anonymousId, "u1"), TrackingConsent.GRANTED)
        controller.onUserInfo(UserInfo(anonymousId, "u2"), TrackingConsent.GRANTED)
        verify(call, times(2)).execute()
    }

    @Test
    fun `consent and foreground gate requests`() {
        controller.start(false)
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
        controller.start(true)
        controller.onUserInfo(UserInfo(anonymousId, "u1"), TrackingConsent.GRANTED)
        verify(
            executor,
            never()
        ).schedule(any<Runnable>(), org.mockito.kotlin.eq(5_000L), org.mockito.kotlin.eq(TimeUnit.MILLISECONDS))
        controller.onUserInfo(UserInfo(anonymousId, "u2"), TrackingConsent.GRANTED)
        verify(
            executor
        ).schedule(any<Runnable>(), org.mockito.kotlin.eq(5_000L), org.mockito.kotlin.eq(TimeUnit.MILLISECONDS))
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
    fun `endpoint preserves string proxy and encodes token`() {
        assertThat(ActiveBeaconController.buildEndpoint("https://example.com/api/v2/rum/", "a+b"))
            .isEqualTo("https://example.com/api/v2/rum/active?client_token=a%2Bb")
        val url = ActiveBeaconController.buildEndpoint("https://example.com/proxy?ddforward=%2Fapi%2Fv2%2Frum", "token")
        assertThat(url).contains("ddforward=%2Fapi%2Fv2%2Frum%2Factive")
    }

    @Test
    fun `rate limit honors backend delay and schedules bounded retry`() {
        whenever(call.execute()).thenReturn(response(429, "Flashcat-Request-Rate-Limit-Delay" to "7"))
        controller.start(true)
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
            if (it.getArgument<Long>(1) == 5_000L || it.getArgument<Long>(1) == 60_000L) retries.add(it.getArgument(0))
            mock<ScheduledFuture<Any>>()
        }
        whenever(call.execute()).thenReturn(response(503), response(503), response(202))
        controller.start(true)
        controller.onUserInfo(UserInfo(anonymousId, "u1"), TrackingConsent.GRANTED)
        retries.removeAt(0).run()
        retries.removeAt(0).run()
        verify(call, times(3)).execute()
    }

    @Test
    fun `queued foreground notification cannot send after background transition`() {
        val queued = mutableListOf<Runnable>()
        whenever(executor.execute(any())).doAnswer {
            queued.add(it.getArgument(0))
            Unit
        }
        controller.start(false)
        controller.onUserInfo(UserInfo(anonymousId, "u1"), TrackingConsent.GRANTED)
        controller.onForeground(true)
        controller.onForeground(false)
        queued.toList().forEach { it.run() }
        verify(factory, never()).newCall(any())
    }

    @Test
    fun `older pending beacon is sent with its age before current identity`() {
        val old = ActiveBeaconController.buildBody(context, UserInfo(anonymousId, "old"))!!
        whenever(prefs.getString(eq("body"), anyOrNull())).thenReturn(old)
        whenever(prefs.getString(eq("identity"), any())).thenReturn("old-key")
        whenever(prefs.getString(eq("day"), any())).thenReturn("old-day")
        whenever(prefs.getLong(eq("created"), any())).thenReturn(System.currentTimeMillis() - 3_000)
        controller.start(true)
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
        controller.start(true)
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
        whenever(call.execute()).thenReturn(response(503), response(202))
        val user = UserInfo(anonymousId, "u1")
        controller.start(true)
        controller.onUserInfo(user, TrackingConsent.GRANTED)
        assertThat(entries).containsKey("body")
        controller.stop()

        val restarted = ActiveBeaconController(
            core, context, app, "app", "https://example.com/api/v2/rum", true, executor, factory, "process"
        )
        restarted.start(true)
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
        controller.start(true)
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
        controller.start(true)
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
        controller.start(true)
        controller.onUserInfo(UserInfo(anonymousId, "u1"), TrackingConsent.GRANTED)
        controller.onForeground(true)
        verify(call, never()).execute()
        verify(editor, never()).putString(eq("body"), any())
        val delays = argumentCaptor<Long>()
        verify(executor, times(3)).schedule(any<Runnable>(), delays.capture(), eq(TimeUnit.MILLISECONDS))
        assertThat(delays.allValues.count { it in 1_000..60_000 }).isEqualTo(1)
    }

    @Test
    fun `consent withdrawal clears the in memory rate limit`() {
        whenever(call.execute()).thenReturn(response(429), response(202))
        controller.start(true)
        controller.onUserInfo(UserInfo(anonymousId, "u1"), TrackingConsent.GRANTED)
        controller.onConsent(TrackingConsent.NOT_GRANTED)
        controller.onConsent(TrackingConsent.GRANTED)
        verify(call, times(2)).execute()
    }

    @Test
    fun `http timeout is retried`() {
        whenever(call.execute()).thenReturn(response(408))
        controller.start(true)
        controller.onUserInfo(UserInfo(anonymousId, "u1"), TrackingConsent.GRANTED)
        verify(executor).schedule(any<Runnable>(), eq(5_000L), eq(TimeUnit.MILLISECONDS))
    }

    private fun response(code: Int, header: Pair<String, String>? = null): Response = Response.Builder().request(
        Request.Builder().url("https://example.com").build()
    )
        .apply { if (header != null) header(header.first, header.second) }
        .protocol(Protocol.HTTP_1_1).code(code).message("test").body("".toResponseBody()).build()
}
