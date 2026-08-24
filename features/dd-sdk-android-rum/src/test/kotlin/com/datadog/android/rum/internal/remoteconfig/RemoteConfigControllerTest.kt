/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.remoteconfig

import com.datadog.android.api.feature.FeatureSdkCore
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.IOException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class RemoteConfigControllerTest {

    private lateinit var store: RemoteConfigStore
    private lateinit var executor: ScheduledExecutorService
    private lateinit var callFactory: Call.Factory
    private lateinit var call: Call
    private var restarts = 0
    private var elapsedMs = 0L
    private lateinit var testedController: RemoteConfigController

    @BeforeEach
    fun setUp() {
        store = mock()
        // Stubbed explicitly rather than left to the mock's default: what "nothing stored" means is
        // the whole point of several of these tests, and a default that is not null would quietly
        // turn them into tests of something else.
        whenever(store.sessionSampleRate()).thenReturn(null)
        whenever(store.sessionReplaySampleRate()).thenReturn(null)
        restarts = 0
        elapsedMs = 0L
        executor = mock()
        callFactory = mock()
        call = mock()
        whenever(callFactory.newCall(any())).thenReturn(call)
        val sdkCore = mock<FeatureSdkCore>()
        whenever(sdkCore.internalLogger).thenReturn(mock())
        testedController = RemoteConfigController(
            sdkCore = sdkCore,
            configUrl = "https://example.com/api/v2/rum/config",
            store = store,
            initialSessionSampleRate = INIT_SESSION_RATE,
            callFactory = callFactory,
            executor = executor,
            restartSession = { restarts++ },
            elapsedTimeMs = { elapsedMs },
            jitter = { 0.5 }
        )
    }

    // region storing

    @Test
    fun `M store the rates the response carries W apply()`() {
        testedController.apply(body(rum = """"sessionSampleRate":42,"sessionReplaySampleRate":7"""))

        verify(store).store(RemoteConfigValues(42f, 7f, 3))
    }

    @Test
    fun `M store a zero rate W apply() { zero is a setting, not a missing value }`() {
        testedController.apply(body(rum = """"sessionSampleRate":0"""))

        verify(store).store(RemoteConfigValues(0f, null, 3))
    }

    @Test
    fun `M leave a rate absent W apply() { response omits it }`() {
        // An absent rate must fall back to what the app passed to init. Writing a zero in its place
        // would silently stop collection nobody asked to stop.
        testedController.apply(body(rum = """"sessionSampleRate":42"""))

        verify(store).store(RemoteConfigValues(42f, null, 3))
    }

    @Test
    fun `M ignore a rate outside 0-100 W apply()`() {
        testedController.apply(body(rum = """"sessionSampleRate":420"""))

        verify(store).store(RemoteConfigValues(null, null, 3))
    }

    @Test
    fun `M forget the rates W apply() { remote configuration switched off }`() {
        testedController.apply(body(enabled = false, rum = """"sessionSampleRate":42"""))

        verify(store).store(RemoteConfigValues(null, null, 3))
    }

    // endregion

    // region activation

    @Test
    fun `M leave the running session alone W apply() { activation is next_session }`() {
        whenever(store.sessionSampleRate()).thenReturn(10f)

        testedController.apply(body(activation = "next_session", rum = """"sessionSampleRate":100"""))

        assertThat(restarts).isZero()
    }

    @Test
    fun `M restart the session W apply() { activation is immediate and the rate changed }`() {
        whenever(store.sessionSampleRate()).thenReturn(10f)

        testedController.apply(body(activation = "immediate", rum = """"sessionSampleRate":100"""))

        assertThat(restarts).isOne()
    }

    @Test
    fun `M leave the running session alone W apply() { immediate but nothing changed }`() {
        // A console resending an unchanged configuration must not cut every session in two.
        whenever(store.sessionSampleRate()).thenReturn(100f)

        testedController.apply(body(activation = "immediate", rum = """"sessionSampleRate":100"""))

        assertThat(restarts).isZero()
    }

    @Test
    fun `M leave the running session alone W apply() { immediate rate equals the init rate }`() {
        whenever(store.sessionSampleRate()).thenReturn(null)

        testedController.apply(body(activation = "immediate", rum = """"sessionSampleRate":$INIT_SESSION_RATE"""))

        assertThat(restarts).isZero()
    }

    @Test
    fun `M restart the session W apply() { immediate and only the replay rate changed }`() {
        whenever(store.sessionSampleRate()).thenReturn(null)
        whenever(store.sessionReplaySampleRate()).thenReturn(10f)

        testedController.apply(
            body(activation = "immediate", rum = """"sessionSampleRate":$INIT_SESSION_RATE,"sessionReplaySampleRate":90""")
        )

        assertThat(restarts).isOne()
    }

    @Test
    fun `M restart the session W apply() { immediate and the kill switch takes the rates away }`() {
        whenever(store.sessionSampleRate()).thenReturn(100f)

        testedController.apply(body(activation = "immediate", enabled = false))

        assertThat(restarts).isOne()
    }

    // endregion

    @Test
    fun `M keep the version W apply() { remote configuration switched off }`() {
        // The rates are gone, but the console still needs to see this client is up to date with
        // the change that turned them off.
        testedController.apply(body(enabled = false))

        verify(store).store(RemoteConfigValues(null, null, 3))
    }

    // region fetching

    @Test
    fun `M fetch right away W start()`() {
        testedController.start()

        verify(executor).execute(any())
    }

    @Test
    fun `M never run two fetches at once W start() { previous one still running }`() {
        testedController.start()
        testedController.onSessionStarted()

        // The captured runnable never ran, so the first fetch is still in flight and the second
        // trigger must not pile another request on top of it.
        verify(executor).execute(any())
    }

    @Test
    fun `M store what the server answered W fetch succeeds`() {
        whenever(call.execute()).thenReturn(response(200, body(rum = """"sessionSampleRate":42""")))

        runPendingFetch()

        verify(store).store(RemoteConfigValues(42f, null, 3))
    }

    @Test
    fun `M tell the server which version is applied W fetch`() {
        whenever(store.appliedVersion()).thenReturn(7)
        whenever(call.execute()).thenReturn(response(200, body()))

        runPendingFetch()

        argumentCaptor<Request> {
            verify(callFactory).newCall(capture())
            assertThat(firstValue.url.toString()).contains("applied_version=7")
        }
    }

    @Test
    fun `M not retry W fetch succeeds`() {
        whenever(call.execute()).thenReturn(response(200, body()))

        runPendingFetch()

        verify(executor, never()).schedule(any<Runnable>(), any(), any())
    }

    @Test
    fun `M keep the stored values W fetch fails`() {
        whenever(call.execute()).thenThrow(IOException("no route to host"))

        runPendingFetch()

        verify(store, never()).store(any())
    }

    @Test
    fun `M retry quickly W fetch fails`() {
        whenever(call.execute()).thenThrow(IOException("no route to host"))

        runPendingFetch()

        // No jitter at 0.5: the first retry is exactly the quick one.
        verify(executor).schedule(any<Runnable>(), eq(5L), eq(TimeUnit.SECONDS))
    }

    @Test
    fun `M retry patiently W the quick retry also fails`() {
        whenever(call.execute()).thenThrow(IOException("no route to host"))

        runPendingFetch()
        whenever(executor.schedule(any<Runnable>(), any(), any())).thenReturn(mock<ScheduledFuture<*>>())
        runPendingRetry()

        verify(executor).schedule(any<Runnable>(), eq(60L), eq(TimeUnit.SECONDS))
    }

    @Test
    fun `M stop retrying until the next trigger W the patient retry also fails`() {
        whenever(call.execute()).thenThrow(IOException("no route to host"))

        runPendingFetch()
        whenever(executor.schedule(any<Runnable>(), any(), any())).thenReturn(mock<ScheduledFuture<*>>())
        runPendingRetry()
        runPendingRetry()

        // Two retries were scheduled (5s and 60s) and no third one ever is.
        verify(executor, times(2)).schedule(any<Runnable>(), any(), any())
    }

    @Test
    fun `M re-arm the backoff W onSessionStarted() { a retry was still waiting }`() {
        whenever(call.execute()).thenThrow(IOException("no route to host"))
        val pendingRetry = mock<ScheduledFuture<*>>()
        whenever(executor.schedule(any<Runnable>(), any(), any())).thenReturn(pendingRetry)

        runPendingFetch()
        testedController.onSessionStarted()

        verify(pendingRetry).cancel(false)
        // The trigger runs its own fetch right away instead of waiting out the retry.
        verify(executor, times(2)).execute(any())
    }

    @Test
    fun `M spread the retry by plus-minus 20 percent W jittered()`() {
        assertThat(RemoteConfigController.jittered(5L, 0.0)).isEqualTo(4L)
        assertThat(RemoteConfigController.jittered(5L, 1.0)).isEqualTo(6L)
        assertThat(RemoteConfigController.jittered(60L, 0.0)).isEqualTo(48L)
        assertThat(RemoteConfigController.jittered(60L, 1.0)).isEqualTo(72L)
    }

    // endregion

    // region coming back to the foreground

    @Test
    fun `M ask again W refreshIfStale() { allowed and what we hold outlived its ttl }`() {
        testedController.apply(body(ttl = 60, refreshOnForeground = true))

        elapsedMs = 61_000L
        testedController.refreshIfStale()

        verify(executor).execute(any())
    }

    @Test
    fun `M ask nothing W refreshIfStale() { not allowed }`() {
        // Off by default: returning to the foreground bunches requests at the moment everyone
        // opens the app, which is the shape the endpoint copes with worst.
        testedController.apply(body(ttl = 60))

        elapsedMs = 61_000L
        testedController.refreshIfStale()

        verify(executor, never()).execute(any())
    }

    @Test
    fun `M ask nothing W refreshIfStale() { what we hold is still fresh }`() {
        // Switching apps back and forth must not turn into a request each time.
        testedController.apply(body(ttl = 300, refreshOnForeground = true))

        elapsedMs = 10_000L
        testedController.refreshIfStale()

        verify(executor, never()).execute(any())
    }

    @Test
    fun `M follow the server ttl for staleness W refreshIfStale()`() {
        testedController.apply(body(ttl = 42, refreshOnForeground = true))

        elapsedMs = 41_000L
        testedController.refreshIfStale()
        elapsedMs = 43_000L
        testedController.refreshIfStale()

        verify(executor).execute(any())
    }

    @Test
    fun `M fall back to the default ttl for staleness W refreshIfStale() { server sent none }`() {
        testedController.apply(body(ttl = 0, refreshOnForeground = true))

        elapsedMs = RemoteConfigController.DEFAULT_TTL_SECONDS * 1_000L - 1
        testedController.refreshIfStale()
        elapsedMs = RemoteConfigController.DEFAULT_TTL_SECONDS * 1_000L + 1
        testedController.refreshIfStale()

        verify(executor).execute(any())
    }

    // endregion

    // region url

    @Test
    fun `M put the configuration beside the intake W buildConfigUrl()`() {
        val url = RemoteConfigController.buildConfigUrl(
            intakeUrl = "https://rum.example.com/api/v2/rum",
            clientToken = "token",
            env = "staging",
            appVersion = "1.2.3"
        )

        assertThat(url).startsWith("https://rum.example.com/api/v2/rum/config?")
        assertThat(url).contains("client_token=token")
        assertThat(url).contains("sdk=android")
        assertThat(url).contains("env=staging")
        assertThat(url).contains("app_version=1.2.3")
    }

    @Test
    fun `M leave out what the app did not set W buildConfigUrl()`() {
        val url = RemoteConfigController.buildConfigUrl(
            intakeUrl = "https://rum.example.com/api/v2/rum",
            clientToken = "token",
            env = "",
            appVersion = ""
        )

        assertThat(url).doesNotContain("env=")
        assertThat(url).doesNotContain("app_version=")
    }

    @Test
    fun `M store the custom bag verbatim W apply()`() {
        testedController.apply(body(custom = """{"viplist":["u-1","u-2"],"debug":true}"""))

        argumentCaptor<RemoteConfigValues> {
            verify(store).store(capture())
            assertThat(JSONObject(firstValue.custom!!).getBoolean("debug")).isTrue()
            assertThat(JSONObject(firstValue.custom!!).getJSONArray("viplist").length()).isEqualTo(2)
        }
    }

    @Test
    fun `M drop the custom bag W apply() { remote configuration switched off }`() {
        testedController.apply(body(enabled = false, custom = """{"debug":true}"""))

        argumentCaptor<RemoteConfigValues> {
            verify(store).store(capture())
            assertThat(firstValue.custom).isNull()
        }
    }

    // endregion

    // region test helpers

    /**
     * Runs the runnable the controller handed to the executor: the fetch it would do on a worker
     * thread in a running app.
     */
    private fun runPendingFetch() {
        testedController.start()
        argumentCaptor<Runnable> {
            verify(executor).execute(capture())
            firstValue.run()
        }
    }

    /**
     * Runs the runnable the controller scheduled as a retry after a failed fetch.
     */
    private fun runPendingRetry() {
        argumentCaptor<Runnable> {
            verify(executor, org.mockito.kotlin.atLeastOnce()).schedule(capture(), any(), any())
            lastValue.run()
        }
    }

    private fun response(code: Int, payload: String): Response =
        Response.Builder()
            .request(Request.Builder().url("https://example.com/api/v2/rum/config").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("OK")
            .body(payload.toResponseBody("application/json".toMediaType()))
            .build()

    private fun body(
        ttl: Int = 300,
        enabled: Boolean = true,
        activation: String = "next_session",
        refreshOnForeground: Boolean = false,
        rum: String = "",
        custom: String? = null
    ): String =
        """{"version":3,"ttl":$ttl,"enabled":$enabled,"activation":"$activation",""" +
            """"refresh_on_foreground":$refreshOnForeground,"rum":{$rum}""" +
            (if (custom == null) "" else ""","custom":$custom""") + "}"

    // endregion

    companion object {
        private const val INIT_SESSION_RATE = 20f
    }
}
