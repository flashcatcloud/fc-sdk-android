/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.remoteconfig

import com.datadog.android.api.feature.FeatureSdkCore
import okhttp3.Call
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class RemoteSamplingControllerTest {

    private lateinit var store: RemoteSamplingStore
    private lateinit var executor: ScheduledExecutorService
    private var restarts = 0
    private var elapsedMs = 0L
    private lateinit var testedController: RemoteSamplingController

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
        testedController = RemoteSamplingController(
            sdkCore = mock<FeatureSdkCore>(),
            configUrl = "https://example.com/api/v2/rum/config",
            store = store,
            initialSessionSampleRate = INIT_SESSION_RATE,
            callFactory = mock<Call.Factory>(),
            executor = executor,
            restartSession = { restarts++ },
            elapsedTimeMs = { elapsedMs }
        )
    }

    // region storing

    @Test
    fun `M store the rates the response carries W apply()`() {
        testedController.apply(body(rum = """"sessionSampleRate":42,"sessionReplaySampleRate":7"""))

        verify(store).store(RemoteSamplingRates(42f, 7f, 3))
    }

    @Test
    fun `M store a zero rate W apply() { zero is a setting, not a missing value }`() {
        testedController.apply(body(rum = """"sessionSampleRate":0"""))

        verify(store).store(RemoteSamplingRates(0f, null, 3))
    }

    @Test
    fun `M leave a rate absent W apply() { response omits it }`() {
        // An absent rate must fall back to what the app passed to init. Writing a zero in its place
        // would silently stop collection nobody asked to stop.
        testedController.apply(body(rum = """"sessionSampleRate":42"""))

        verify(store).store(RemoteSamplingRates(42f, null, 3))
    }

    @Test
    fun `M ignore a rate outside 0-100 W apply()`() {
        testedController.apply(body(rum = """"sessionSampleRate":420"""))

        verify(store).store(RemoteSamplingRates(null, null, 3))
    }

    @Test
    fun `M forget the rates W apply() { remote configuration switched off }`() {
        testedController.apply(body(enabled = false, rum = """"sessionSampleRate":42"""))

        verify(store).store(RemoteSamplingRates(null, null, 3))
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
        // A console resending an unchanged configuration on every poll must not cut every session
        // in two.
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

    // region ttl

    @Test
    fun `M follow the server ttl W apply()`() {
        assertThat(testedController.apply(body(ttl = 42))).isEqualTo(42L)
    }

    @Test
    fun `M fall back to the default ttl W apply() { server sent none }`() {
        assertThat(testedController.apply(body(ttl = 0))).isEqualTo(RemoteSamplingController.DEFAULT_TTL_SECONDS)
    }

    // endregion

    @Test
    fun `M keep the version W apply() { remote configuration switched off }`() {
        // The rates are gone, but the console still needs to see this client is up to date with
        // the change that turned them off.
        testedController.apply(body(enabled = false))

        verify(store).store(RemoteSamplingRates(null, null, 3))
    }

    // region coming back to the foreground

    @Test
    fun `M ask again W refreshIfStale() { allowed and what we hold outlived its ttl }`() {
        // An app in the background may not have had its poll timer run for hours, so returning to
        // the foreground is its own reason to ask.
        testedController.start()
        testedController.apply(body(ttl = 60, refreshOnForeground = true))
        reset(executor)

        elapsedMs = 61_000L
        testedController.refreshIfStale()

        verify(executor).schedule(any(), eq(0L), eq(TimeUnit.SECONDS))
    }

    @Test
    fun `M ask nothing W refreshIfStale() { not allowed }`() {
        // Off by default: returning to the foreground bunches requests at the moment everyone
        // opens the app, which is the shape the endpoint copes with worst.
        testedController.start()
        testedController.apply(body(ttl = 60))
        reset(executor)

        elapsedMs = 61_000L
        testedController.refreshIfStale()

        verify(executor, never()).schedule(any(), any(), any())
    }

    @Test
    fun `M ask nothing W refreshIfStale() { what we hold is still fresh }`() {
        // Switching apps back and forth must not turn into a request each time.
        testedController.start()
        testedController.apply(body(ttl = 300, refreshOnForeground = true))
        reset(executor)

        elapsedMs = 10_000L
        testedController.refreshIfStale()

        verify(executor, never()).schedule(any(), any(), any())
    }

    // endregion

    // region url

    @Test
    fun `M put the configuration beside the intake W buildConfigUrl()`() {
        val url = RemoteSamplingController.buildConfigUrl(
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
        val url = RemoteSamplingController.buildConfigUrl(
            intakeUrl = "https://rum.example.com/api/v2/rum",
            clientToken = "token",
            env = "",
            appVersion = ""
        )

        assertThat(url).doesNotContain("env=")
        assertThat(url).doesNotContain("app_version=")
    }

    // endregion

    private fun body(
        ttl: Int = 300,
        enabled: Boolean = true,
        activation: String = "next_session",
        refreshOnForeground: Boolean = false,
        rum: String = ""
    ): String =
        """{"version":3,"ttl":$ttl,"enabled":$enabled,"activation":"$activation",""" +
            """"refresh_on_foreground":$refreshOnForeground,"rum":{$rum}}"""

    companion object {
        private const val INIT_SESSION_RATE = 20f
    }
}
