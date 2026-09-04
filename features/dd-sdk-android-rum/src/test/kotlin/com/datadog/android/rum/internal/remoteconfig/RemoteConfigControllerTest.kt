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
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
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
        restarts = 0
        elapsedMs = 0L
        executor = mock()
        callFactory = mock()
        call = mock()
        whenever(callFactory.newCall(any())).thenReturn(call)
        testedController = controllerWithInitialRate(INIT_SESSION_RATE)
    }

    private fun controllerWithInitialRate(initialSessionSampleRate: Float): RemoteConfigController {
        val sdkCore = mock<FeatureSdkCore>()
        whenever(sdkCore.internalLogger).thenReturn(mock())
        return RemoteConfigController(
            sdkCore = sdkCore,
            configUrl = "https://example.com/api/v2/rum/config",
            store = store,
            initialSessionSampleRate = initialSessionSampleRate,
            callFactory = callFactory,
            executor = executor,
            restartSession = { restarts++ },
            elapsedTimeMs = { elapsedMs },
            jitter = { 0.5 }
        )
    }

    // region storing

    @Test
    fun `M store the rate the response carries W apply()`() {
        testedController.apply(body(rum = """"sessionSampleRate":42"""))

        verify(store).store(RemoteConfigValues(42f, 3, ttlSeconds = 300L))
    }

    @Test
    fun `M store a zero rate W apply() { zero is a setting, not a missing value }`() {
        testedController.apply(body(rum = """"sessionSampleRate":0"""))

        verify(store).store(RemoteConfigValues(0f, 3, ttlSeconds = 300L))
    }

    @Test
    fun `M leave the rate absent W apply() { response omits it }`() {
        // An absent rate must fall back to what the app passed to init. Writing a zero in its place
        // would silently stop collection nobody asked to stop.
        testedController.apply(body(rum = ""))

        verify(store).store(RemoteConfigValues(null, 3, ttlSeconds = 300L))
    }

    @Test
    fun `M ignore a rate outside 0-100 W apply()`() {
        testedController.apply(body(rum = """"sessionSampleRate":420"""))

        verify(store).store(RemoteConfigValues(null, 3, ttlSeconds = 300L))
    }

    @Test
    fun `M forget the rates W apply() { remote configuration switched off }`() {
        testedController.apply(body(enabled = false, rum = """"sessionSampleRate":42"""))

        verify(store).store(RemoteConfigValues(null, 3, ttlSeconds = 300L))
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
    fun `M restart the session W apply() { immediate and the kill switch takes the rates away }`() {
        whenever(store.sessionSampleRate()).thenReturn(100f)

        testedController.apply(body(activation = "immediate", enabled = false))

        assertThat(restarts).isOne()
    }

    @Test
    fun `M restart the session W apply() { next_session but the rate leaves zero }`() {
        // Nothing was being collected while the rate was zero, so there is no session worth
        // preserving and no winner to spare by re-drawing everyone at the new rate. Waiting here
        // would show an operator who has just switched collection on nothing at all.
        whenever(store.sessionSampleRate()).thenReturn(0f)

        testedController.apply(body(activation = "next_session", rum = """"sessionSampleRate":100"""))

        assertThat(restarts).isOne()
    }

    @Test
    fun `M restart the session W apply() { next_session but the rate reaches zero }`() {
        // The emergency stop. One that took until the session happened to rotate would not be one.
        whenever(store.sessionSampleRate()).thenReturn(100f)

        testedController.apply(body(activation = "next_session", rum = """"sessionSampleRate":0"""))

        assertThat(restarts).isOne()
    }

    @Test
    fun `M restart the session W apply() { next_session and init never collected }`() {
        // The application whose rate only ever comes from the console: nothing is stored yet, so
        // the rate leaving zero is the init value being replaced rather than a stored one.
        whenever(store.sessionSampleRate()).thenReturn(null)
        testedController = controllerWithInitialRate(0f)

        testedController.apply(body(activation = "next_session", rum = """"sessionSampleRate":100"""))

        assertThat(restarts).isOne()
    }

    @Test
    fun `M restart the session W apply() { next_session and the kill switch hands zero back to init }`() {
        // Switching remote configuration off returns the decision to the value the app was built
        // with, and that is a rate leaving zero like any other.
        whenever(store.sessionSampleRate()).thenReturn(0f)

        testedController.apply(body(activation = "next_session", enabled = false))

        assertThat(restarts).isOne()
    }

    @Test
    fun `M leave the running session alone W apply() { next_session and the rate stays at zero }`() {
        // Otherwise every announcement would cut one empty session after another in two for as long
        // as collection stayed switched off.
        whenever(store.sessionSampleRate()).thenReturn(0f)

        testedController.apply(body(activation = "next_session", rum = """"sessionSampleRate":0"""))

        assertThat(restarts).isZero()
    }

    @Test
    fun `M leave the running session alone W apply() { next_session and neither rate is zero }`() {
        // No rate but zero says anything about whether THIS session should have been kept: only a
        // second draw could, and drawing twice turns a rate p into p squared.
        whenever(store.sessionSampleRate()).thenReturn(30f)

        testedController.apply(body(activation = "next_session", rum = """"sessionSampleRate":80"""))

        assertThat(restarts).isZero()
    }

    // endregion

    @Test
    fun `M keep the version W apply() { remote configuration switched off }`() {
        // The rates are gone, but the console still needs to see this client is up to date with
        // the change that turned them off.
        testedController.apply(body(enabled = false))

        verify(store).store(RemoteConfigValues(null, 3, ttlSeconds = 300L))
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
    fun `M keep the stored values and ask again W fetch() { server answers with an error }`() {
        // Neither a 200 nor a 304 is an answer about the configuration. Nothing may be stored, and
        // the ask is owed a retry - an endpoint having a bad minute must not move anybody's rates.
        whenever(call.execute()).thenReturn(response(500, ""))

        runPendingFetch()

        verify(store, never()).store(any())
        verify(store, never()).touch()
        verify(executor).schedule(any(), any(), any())
    }

    @Test
    fun `M store what the server answered W fetch succeeds`() {
        whenever(call.execute()).thenReturn(response(200, body(rum = """"sessionSampleRate":42""")))

        runPendingFetch()

        verify(store).store(RemoteConfigValues(42f, 3, ttlSeconds = 300L))
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
    fun `M offer the stored validator W fetch { one was stored }`() {
        whenever(store.etag()).thenReturn("\"abc123\"")
        whenever(call.execute()).thenReturn(response(200, body()))

        runPendingFetch()

        argumentCaptor<Request> {
            verify(callFactory).newCall(capture())
            assertThat(firstValue.header("If-None-Match")).isEqualTo("\"abc123\"")
        }
    }

    @Test
    fun `M offer no validator W fetch { none was stored }`() {
        whenever(store.etag()).thenReturn(null)
        whenever(call.execute()).thenReturn(response(200, body()))

        runPendingFetch()

        argumentCaptor<Request> {
            verify(callFactory).newCall(capture())
            assertThat(firstValue.header("If-None-Match")).isNull()
        }
    }

    @Test
    fun `M store the refresh rhythm alongside the values W apply()`() {
        // Kept on disk rather than in memory because an unchanged answer is a 304 with no body:
        // a memory-only copy is back at its default on every launch after the first.
        testedController.apply(body(ttl = 60, refreshOnForeground = true, rum = """"sessionSampleRate":42"""))

        verify(store).store(
            RemoteConfigValues(42f, 3, ttlSeconds = 60L, refreshOnForeground = true)
        )
    }

    @Test
    fun `M store the refresh rhythm W apply() { remote configuration switched off }`() {
        // The server goes on saying when to ask again while the feature is off; a client that
        // stopped honouring that would never learn it had been switched back on.
        testedController.apply(body(enabled = false, ttl = 60, refreshOnForeground = true))

        verify(store).store(
            RemoteConfigValues(null, 3, ttlSeconds = 60L, refreshOnForeground = true)
        )
    }

    @Test
    fun `M keep the stored values and call it a success W fetch answers not modified`() {
        whenever(call.execute()).thenReturn(response(304, ""))

        runPendingFetch()

        verify(store, never()).store(any())
        verify(executor, never()).schedule(any<Runnable>(), any(), any())
    }

    @Test
    fun `M mark the entry as still in use W fetch answers not modified`() {
        // The one answer that stores nothing. A settled client meets it at almost every fetch, and
        // the sweep reads nothing but age, so without this its entry would stop looking in use.
        whenever(call.execute()).thenReturn(response(304, ""))

        runPendingFetch()

        verify(store).touch()
    }

    @Test
    fun `M clear the entries of versions the device no longer runs W the first fetch`() {
        whenever(call.execute()).thenReturn(response(200, body()))

        runPendingFetch()

        verify(store).sweepAbandoned()
    }

    @Test
    fun `M go on fetching W housekeeping throws`() {
        // Every later fetch is gated on the in-flight flag, so anything that escaped without
        // clearing it would end remote configuration for the rest of the process — silently.
        whenever(store.sweepAbandoned()).thenThrow(RuntimeException("preferences are having a day"))
        whenever(call.execute()).thenReturn(response(200, body()))

        testedController.start()
        argumentCaptor<Runnable> {
            verify(executor).execute(capture())
            assertThatThrownBy { firstValue.run() }.isInstanceOf(RuntimeException::class.java)
        }
        testedController.onSessionStarted()

        verify(executor, times(2)).execute(any())
    }

    @Test
    fun `M sweep once a launch W several fetches`() {
        // Walking the preferences file again at every session start would learn nothing new.
        whenever(call.execute()).thenReturn(response(200, body()))

        runPendingFetch()
        testedController.onSessionStarted()
        argumentCaptor<Runnable> {
            verify(executor, times(2)).execute(capture())
            allValues.last().run()
        }

        verify(store, times(1)).sweepAbandoned()
    }

    @Test
    fun `M store the validator the answer came with W fetch succeeds`() {
        whenever(call.execute()).thenReturn(response(200, body(), etag = "\"v42\""))

        runPendingFetch()

        argumentCaptor<RemoteConfigValues> {
            verify(store).store(capture())
            assertThat(firstValue.etag).isEqualTo("\"v42\"")
        }
    }

    @Test
    fun `M keep the validator W apply() { remote configuration switched off }`() {
        // The values are gone, but the validator belongs to the answer that turned them off and is
        // what the next If-None-Match is built from.
        testedController.apply(body(enabled = false), etag = "\"v43\"")

        argumentCaptor<RemoteConfigValues> {
            verify(store).store(capture())
            assertThat(firstValue.sessionSampleRate).isNull()
            assertThat(firstValue.version).isEqualTo(3)
            assertThat(firstValue.etag).isEqualTo("\"v43\"")
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
    fun `M honour the stored rhythm W refreshIfStale() { fresh process, first answer is 304 }`() {
        // The case a settled fleet lives in. The console's permission was granted in some earlier
        // process and is on disk; this process asks, the validator matches, and a 304 comes back
        // with no body to read it from. A controller that only ever learned the rhythm from a body
        // would spend this whole process on the defaults and never refresh on foreground again.
        whenever(store.refreshOnForeground()).thenReturn(true)
        whenever(store.ttlSeconds()).thenReturn(60L)
        whenever(call.execute()).thenReturn(response(304, ""))

        runPendingFetch()
        clearInvocations(executor)
        elapsedMs = 61_000L
        testedController.refreshIfStale()

        verify(executor).execute(any())
    }

    @Test
    fun `M ask nothing W refreshIfStale() { fresh process, nothing was ever stored }`() {
        // The negative control for the test above: with nothing on disk the permission is not
        // assumed, so the very first launch of an app still makes no foreground request.
        whenever(call.execute()).thenReturn(response(304, ""))

        runPendingFetch()
        clearInvocations(executor)
        elapsedMs = 61_000L
        testedController.refreshIfStale()

        verify(executor, never()).execute(any())
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
            appVersion = "1.2.3",
            sdkVersion = "2.26.0"
        )

        assertThat(url).startsWith("https://rum.example.com/api/v2/rum/config?")
        assertThat(url).contains("client_token=token")
        assertThat(url).contains("sdk=android")
        assertThat(url).contains("env=staging")
        assertThat(url).contains("app_version=1.2.3")
        assertThat(url).contains("sdk_version=2.26.0")
    }

    @Test
    fun `M leave out what the app did not set W buildConfigUrl()`() {
        val url = RemoteConfigController.buildConfigUrl(
            intakeUrl = "https://rum.example.com/api/v2/rum",
            clientToken = "token",
            env = "",
            appVersion = "",
            sdkVersion = ""
        )

        assertThat(url).doesNotContain("env=")
        assertThat(url).doesNotContain("app_version=")
        assertThat(url).doesNotContain("sdk_version=")
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

    // region contract guards

    @Test
    fun `M keep the stored values and ask again W apply() { body is not a configuration }`() {
        val outcome = testedController.apply("<html>captive portal</html>")

        assertThat(outcome).isEqualTo(RemoteConfigController.Outcome.UNREADABLE)
        verify(store, never()).store(any())
        assertThat(restarts).isEqualTo(0)
    }

    @Test
    fun `M not wedge the controller W fetch() { body is not a configuration }`() {
        whenever(call.execute()).thenReturn(response(200, "<html>captive portal</html>"))
        runPendingFetch()

        // The whole point: an unreadable body must leave the controller able to ask again. If the
        // parse escaped, inFlight would still be set and this second trigger would be dropped.
        testedController.onSessionStarted()

        verify(executor, times(2)).execute(any())
    }

    @Test
    fun `M ask again W fetch() { body is not a configuration }`() {
        whenever(call.execute()).thenReturn(response(200, "not json at all"))

        runPendingFetch()

        verify(executor).schedule(any(), any(), any())
    }

    @Test
    fun `M refuse the whole configuration W apply() { schema this SDK does not read }`() {
        val outcome = testedController.apply(
            body(rum = """"sessionSampleRate":42""", schemaVersion = 99)
        )

        assertThat(outcome).isEqualTo(RemoteConfigController.Outcome.UNSUPPORTED_SCHEMA)
        // Nothing of a body we cannot vouch for reaches storage, not even the fields that happened
        // to parse.
        verify(store, never()).store(any())
    }

    @Test
    fun `M refuse the whole configuration W apply() { schema is not a number }`() {
        // org.json would turn "1" into 1 and accept a body the other SDKs refuse. The point of this
        // field is that every reader agrees about the same response.
        val outcome = testedController.apply(
            """{"schema_version":"1","version":3,"enabled":true,"rum":{"sessionSampleRate":42}}"""
        )

        assertThat(outcome).isEqualTo(RemoteConfigController.Outcome.UNSUPPORTED_SCHEMA)
        verify(store, never()).store(any())
    }

    @Test
    fun `M refuse the body W apply() { schema is an explicit null }`() {
        // Absent and null say the same thing: nothing was stamped.
        val outcome = testedController.apply(
            """{"schema_version":null,"version":3,"enabled":true,"rum":{"sessionSampleRate":42}}"""
        )

        assertThat(outcome).isEqualTo(RemoteConfigController.Outcome.UNREADABLE)
        verify(store, never()).store(any())
    }

    @Test
    fun `M refuse the body W apply() { no schema at all }`() {
        // The stamp is the whole of what tells a configuration from any other JSON: every other
        // field is read with a default, so an unrelated body would come out as "switched off, no
        // rates" and empty the entry. Nothing is stored, and the request is asked again for.
        val outcome = testedController.apply(
            body(rum = """"sessionSampleRate":42""", schemaVersion = null)
        )

        assertThat(outcome).isEqualTo(RemoteConfigController.Outcome.UNREADABLE)
        verify(store, never()).store(any())
    }

    @Test
    fun `M keep the stored values and ask again W fetch() { body carries no schema }`() {
        // The negative control for the refusal above: a body this SDK will not read must leave the
        // rates that are working in place and be retried, exactly as an unreachable endpoint is.
        whenever(call.execute())
            .thenReturn(response(200, body(rum = """"sessionSampleRate":42""", schemaVersion = null)))

        runPendingFetch()

        verify(store, never()).store(any())
        verify(executor).schedule(any(), any(), any())
    }

    @Test
    fun `M not ask again W fetch() { schema this SDK does not read }`() {
        whenever(call.execute()).thenReturn(response(200, body(schemaVersion = 99)))

        runPendingFetch()

        // Retrying would fetch the same refusal. The server answered; this SDK simply cannot use
        // the answer until it is updated.
        verify(executor, never()).schedule(any(), any(), any())
    }

    @Test
    fun `M apply the configuration W apply() { schema this SDK reads }`() {
        val outcome = testedController.apply(body(rum = """"sessionSampleRate":42"""))

        assertThat(outcome).isEqualTo(RemoteConfigController.Outcome.APPLIED)
        verify(store).store(any())
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

    private fun response(code: Int, payload: String, etag: String? = null): Response =
        Response.Builder()
            .request(Request.Builder().url("https://example.com/api/v2/rum/config").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("OK")
            .apply { if (etag != null) header("ETag", etag) }
            .body(payload.toResponseBody("application/json".toMediaType()))
            .build()

    private fun body(
        ttl: Int = 300,
        enabled: Boolean = true,
        activation: String = "next_session",
        refreshOnForeground: Boolean = false,
        rum: String = "",
        custom: String? = null,
        schemaVersion: Int? = RemoteConfigController.SUPPORTED_SCHEMA_VERSION
    ): String =
        "{" + (if (schemaVersion == null) "" else """"schema_version":$schemaVersion,""") +
            """"version":3,"ttl":$ttl,"enabled":$enabled,"activation":"$activation",""" +
            """"refresh_on_foreground":$refreshOnForeground,"rum":{$rum}""" +
            (if (custom == null) "" else ""","custom":$custom""") + "}"

    // endregion

    companion object {
        private const val INIT_SESSION_RATE = 20f
    }
}
