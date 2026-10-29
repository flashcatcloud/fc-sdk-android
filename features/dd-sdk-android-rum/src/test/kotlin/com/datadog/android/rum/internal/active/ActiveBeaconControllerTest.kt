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
        ).schedule(any<Runnable>(), org.mockito.kotlin.eq(5L), org.mockito.kotlin.eq(TimeUnit.SECONDS))
        controller.onUserInfo(UserInfo(anonymousId, "u2"), TrackingConsent.GRANTED)
        verify(executor).schedule(any<Runnable>(), org.mockito.kotlin.eq(5L), org.mockito.kotlin.eq(TimeUnit.SECONDS))
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
    fun `rate limit persists retry time without scheduling an immediate retry`() {
        whenever(call.execute()).thenReturn(response(429))
        controller.start(true)
        controller.onUserInfo(UserInfo(anonymousId, "u1"), TrackingConsent.GRANTED)
        verify(editor).putLong(eq("next_allowed"), any())
        verify(executor, never()).schedule(any<Runnable>(), eq(5L), eq(TimeUnit.SECONDS))
    }

    @Test
    fun `pending age uses monotonic clock and discards suspicious wall clock changes`() {
        assertThat(ActiveBeaconController.pendingAge(12_000, 7_000, 10_000, 5_000)).isEqualTo(2_000)
        assertThat(ActiveBeaconController.pendingAge(500_000, 7_000, 10_000, 5_000)).isNull()
        assertThat(ActiveBeaconController.pendingAge(12_000, 1_000, 10_000, 5_000)).isEqualTo(2_000)
        assertThat(ActiveBeaconController.pendingAge(9_000, 7_000, 10_000, 5_000)).isNull()
    }

    @Test
    fun `network failure is retried twice and then accepted`() {
        val retries = mutableListOf<Runnable>()
        whenever(executor.schedule(any<Runnable>(), any(), any())).doAnswer {
            if (it.getArgument<TimeUnit>(2) == TimeUnit.SECONDS) retries.add(it.getArgument(0))
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

    private fun response(code: Int): Response = Response.Builder().request(
        Request.Builder().url("https://example.com").build()
    )
        .protocol(Protocol.HTTP_1_1).code(code).message("test").body("".toResponseBody()).build()
}
