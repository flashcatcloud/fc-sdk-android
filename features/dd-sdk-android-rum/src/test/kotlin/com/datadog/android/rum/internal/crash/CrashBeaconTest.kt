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
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.Call
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Timeout
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class CrashBeaconTest {

    @Mock
    lateinit var mockCallFactory: Call.Factory

    @Mock
    lateinit var mockCall: Call

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    private lateinit var executor: ExecutorService

    private val configuration = CrashBeacon.Configuration()
    private var inForeground = true

    private val fakeRumContext = RumContext(
        applicationId = "app-id",
        sessionId = "8f2c6b1e-6d55-4f5e-9a51-0c1b8a1e2f3d",
        viewId = "0b6e3c4a-1d2e-4f5a-8b9c-0d1e2f3a4b5c",
        viewName = "CheckoutActivity",
        viewUrl = "com.example.CheckoutActivity",
        sessionState = RumSessionScope.State.TRACKED,
        isSessionActive = true,
        viewTimestampOffset = 120_000L
    )

    private lateinit var testedBeacon: CrashBeacon

    @BeforeEach
    fun `set up`() {
        executor = Executors.newSingleThreadExecutor()
        whenever(mockCallFactory.newCall(any())) doReturn mockCall
        whenever(mockCall.timeout()) doReturn Timeout()
        fakeDatadogContext = fakeDatadogContext.copy(
            trackingConsent = TrackingConsent.GRANTED,
            featuresContext = mapOf(Feature.RUM_FEATURE_NAME to fakeRumContext.toMap())
        )
        testedBeacon = CrashBeacon(
            url = CrashBeacon.buildUrl("https://rum.example.com/api/v2/rum", "token123"),
            applicationId = "app-id",
            callFactory = mockCallFactory,
            executor = executor,
            internalLogger = mockInternalLogger,
            configuration = configuration,
            isInForeground = { inForeground }
        )
    }

    @AfterEach
    fun `tear down`() {
        executor.shutdownNow()
    }

    @Test
    fun `M post the beacon next to the intake and report it sent W send() { intake accepts it }`() {
        whenever(mockCall.execute()) doReturn response(202)

        val result = send()

        assertThat(result.status).isEqualTo(CrashBeacon.STATUS_SENT)
        argumentCaptor<Request> {
            verify(mockCallFactory).newCall(capture())
            assertThat(firstValue.url.toString())
                .isEqualTo("https://rum.example.com/api/v2/rum/crash?client_token=token123")
            assertThat(firstValue.method).isEqualTo("POST")
        }
    }

    @Test
    fun `M send the crash id and the launch the late report will carry W send()`() {
        whenever(mockCall.execute()) doReturn response(202)
        val offset = fakeRumContext.viewTimestampOffset

        send(crashTimeMs = 1_000_000L, timeSinceAppStartMs = 4_000L)

        val body = argumentCaptor<Request>().run {
            verify(mockCallFactory).newCall(capture())
            JSONObject(Buffer().also { firstValue.body!!.writeTo(it) }.readUtf8())
        }
        assertThat(body.getString("crash_id")).isEqualTo(testedBeacon.crashId)
        assertThat(body.getLong("date")).isEqualTo(1_000_000L + offset)
        assertThat(body.getLong("launch_time")).isEqualTo(1_000_000L + offset - 4_000L)
        assertThat(body.getString("crash_type")).isEqualTo(IllegalStateException::class.java.canonicalName)
        assertThat(body.getString("crash_message")).isEqualTo("boom")
        assertThat(body.getString("application_id")).isEqualTo("app-id")
        assertThat(body.getString("session_id")).isEqualTo(fakeRumContext.sessionId)
        assertThat(body.getString("view_id")).isEqualTo(fakeRumContext.viewId)
        assertThat(body.getString("view_name")).isEqualTo(fakeRumContext.viewName)
        assertThat(body.getString("version")).isEqualTo(fakeDatadogContext.version)
        assertThat(body.getString("service")).isEqualTo(fakeDatadogContext.service)
        assertThat(body.getString("sdk_version")).isEqualTo(fakeDatadogContext.sdkVersion)
        assertThat(body.getString("source")).isEqualTo(fakeDatadogContext.source)
    }

    @Test
    fun `M report it failed W send() { intake refuses it }`() {
        whenever(mockCall.execute()) doReturn response(500)

        assertThat(send().status).isEqualTo(CrashBeacon.STATUS_FAILED)
    }

    @Test
    fun `M report it failed W send() { request fails }`() {
        whenever(mockCall.execute()) doThrow IOException("unreachable")

        assertThat(send().status).isEqualTo(CrashBeacon.STATUS_FAILED)
    }

    @Test
    fun `M stop waiting at the timeout W send() { intake does not answer }`() {
        val release = CountDownLatch(1)
        whenever(mockCall.execute()) doAnswer {
            release.await(5, TimeUnit.SECONDS)
            response(202)
        }
        configuration.timeoutMs = 500

        val start = System.nanoTime()
        val result = send()
        val waitedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
        release.countDown()

        assertThat(result.status).isEqualTo(CrashBeacon.STATUS_TIMEOUT)
        assertThat(waitedMs).isLessThan(1_000L)
    }

    @Test
    fun `M skip at once without the beacon thread W send() { background, context read would block }`() {
        inForeground = false
        val start = System.nanoTime()

        val result = checkNotNull(
            testedBeacon.send(
                datadogContext = {
                    Thread.sleep(60_000)
                    fakeDatadogContext
                },
                crashType = "java.lang.IllegalStateException",
                message = "boom",
                crashTimeMs = 1_000_000L,
                timeSinceAppStartMs = 4_000L
            )
        )

        assertThat(result.beacon.status).isEqualTo(CrashBeacon.STATUS_SKIPPED)
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)).isLessThan(200L)
    }

    @Test
    fun `M count front and visible apps only W isUserVisible()`() {
        assertThat(CrashBeacon.isUserVisible(100)).isTrue // IMPORTANCE_FOREGROUND
        assertThat(CrashBeacon.isUserVisible(200)).isTrue // IMPORTANCE_VISIBLE (split screen, PiP)
        assertThat(CrashBeacon.isUserVisible(125)).isFalse // IMPORTANCE_FOREGROUND_SERVICE
        assertThat(CrashBeacon.isUserVisible(400)).isFalse // IMPORTANCE_CACHED
    }

    @Test
    fun `M skip it W send() { app in the background }`() {
        inForeground = false

        assertSkipped()
    }

    @Test
    fun `M skip it W send() { console turned beacons off }`() {
        configuration.enabled = false

        assertSkipped()
    }

    @Test
    fun `M skip it W send() { tracking consent not granted }`() {
        fakeDatadogContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.PENDING)

        assertSkipped()
    }

    @Test
    fun `M skip it W send() { session not tracked }`() {
        fakeDatadogContext = fakeDatadogContext.copy(
            featuresContext = mapOf(
                Feature.RUM_FEATURE_NAME to
                    fakeRumContext.copy(sessionState = RumSessionScope.State.NOT_TRACKED).toMap()
            )
        )

        assertSkipped()
    }

    @Test
    fun `M send no second beacon W send() { a second crash in the same process }`() {
        whenever(mockCall.execute()) doReturn response(202)
        send()

        val second = testedBeacon.send(
            datadogContext = { fakeDatadogContext },
            crashType = "java.lang.IllegalStateException",
            message = "again",
            crashTimeMs = 2_000_000L,
            timeSinceAppStartMs = 5_000L
        )

        assertThat(second).isNull()
        verify(mockCallFactory).newCall(any())
    }

    @Test
    fun `M report it failed without throwing W send() { reading the context throws }`() {
        val result = send(readContext = { throw OutOfMemoryError("no room left") })

        assertThat(result.status).isEqualTo(CrashBeacon.STATUS_FAILED)
        verify(mockCallFactory, never()).newCall(any())
    }

    @Test
    fun `M report it failed W send() { no SDK context }`() {
        assertThat(send(readContext = { null }).status).isEqualTo(CrashBeacon.STATUS_FAILED)
    }

    @Test
    fun `M stop waiting at the timeout W send() { reading the context blocks }`() {
        val release = CountDownLatch(1)
        configuration.timeoutMs = 500

        val start = System.nanoTime()
        val result = send(readContext = {
            release.await(5, TimeUnit.SECONDS)
            fakeDatadogContext
        })
        val waitedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
        release.countDown()

        assertThat(result.status).isEqualTo(CrashBeacon.STATUS_TIMEOUT)
        assertThat(waitedMs).isLessThan(1_000L)
    }

    @Test
    fun `M hand out the beacon's date before the request leaves W send() { onDispatch }`() {
        val dispatched = mutableListOf<Long>()
        whenever(mockCall.execute()) doAnswer {
            assertThat(dispatched).hasSize(1)
            response(202)
        }

        val result = checkNotNull(
            testedBeacon.send(
                datadogContext = { fakeDatadogContext },
                crashType = "SIGSEGV",
                message = "NDK crash detected with signal: SIGSEGV",
                crashTimeMs = 1_000_000L,
                timeSinceAppStartMs = 4_000L,
                onDispatch = { dispatched += it }
            )
        )

        assertThat(dispatched).containsExactly(result.crashDate)
    }

    @Test
    fun `M hand out the crash id before sending W send() { onCrashId }`() {
        val seen = mutableListOf<String>()
        whenever(mockCall.execute()) doAnswer {
            assertThat(seen).containsExactly(testedBeacon.crashId)
            response(202)
        }

        val result = checkNotNull(
            testedBeacon.send(
                datadogContext = { fakeDatadogContext },
                crashType = "SIGSEGV",
                message = "NDK crash detected with signal: SIGSEGV",
                crashTimeMs = 1_000_000L,
                timeSinceAppStartMs = 4_000L,
                onCrashId = { seen += it }
            )
        )

        assertThat(result.beacon.status).isEqualTo(CrashBeacon.STATUS_SENT)
        assertThat(seen).containsExactly(testedBeacon.crashId)
    }

    @Test
    fun `M send nothing W send() { the crash id cannot be recorded }`() {
        val result = checkNotNull(
            testedBeacon.send(
                datadogContext = { fakeDatadogContext },
                crashType = "SIGSEGV",
                message = "NDK crash detected with signal: SIGSEGV",
                crashTimeMs = 1_000_000L,
                timeSinceAppStartMs = 4_000L,
                onCrashId = { throw java.io.IOException("disk full") }
            )
        )

        assertThat(result.beacon.status).isEqualTo(CrashBeacon.STATUS_FAILED)
        verify(mockCallFactory, never()).newCall(any())
    }

    @Test
    fun `M arm with the console's switch and wait W armState()`() {
        assertThat(testedBeacon.armState()).isEqualTo(true to CrashBeacon.DEFAULT_TIMEOUT_MS)

        configuration.enabled = false
        configuration.timeoutMs = 1500

        assertThat(testedBeacon.armState()).isEqualTo(false to 1500)
    }

    @Test
    fun `M draw one crash id per process W crashId`() {
        assertThat(testedBeacon.crashId).isEqualTo(testedBeacon.crashId)
        assertThat(testedBeacon.crashId).matches("^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$")
    }

    @Test
    fun `M send the first crash of a withheld session W send()`() {
        fakeDatadogContext = fakeDatadogContext.copy(
            featuresContext = mapOf(
                Feature.RUM_FEATURE_NAME to fakeRumContext.copy(sessionState = RumSessionScope.State.WITHHELD).toMap()
            )
        )
        whenever(mockCall.execute()) doReturn response(202)

        assertThat(send().status).isEqualTo(CrashBeacon.STATUS_SENT)
        verify(mockCallFactory).newCall(any())
    }

    @Test
    fun `M skip an expired session W send()`() {
        fakeDatadogContext = fakeDatadogContext.copy(
            featuresContext = mapOf(
                Feature.RUM_FEATURE_NAME to fakeRumContext.copy(sessionState = RumSessionScope.State.EXPIRED).toMap()
            )
        )

        assertSkipped()
    }

    @Test
    fun `M skip a stopped withheld session W send()`() {
        fakeDatadogContext = fakeDatadogContext.copy(
            featuresContext = mapOf(
                Feature.RUM_FEATURE_NAME to fakeRumContext.copy(
                    sessionState = RumSessionScope.State.WITHHELD,
                    isSessionActive = false
                ).toMap()
            )
        )

        assertSkipped()
    }

    @Test
    fun `M never read or send unfiltered context W send() { custom error mapper }`() {
        testedBeacon = CrashBeacon(
            url = "https://rum.example.com/api/v2/rum/crash",
            applicationId = "app-id",
            callFactory = mockCallFactory,
            executor = executor,
            internalLogger = mockInternalLogger,
            configuration = configuration,
            isInForeground = { true },
            hasCustomErrorEventMapper = true
        )

        val result = send(readContext = { error("Must not read unfiltered context") })

        assertThat(result.status).isEqualTo(CrashBeacon.STATUS_SKIPPED)
        assertThat(result.duration).isZero()
        verify(mockCallFactory, never()).newCall(any())
    }

    @Test
    fun `M keep the launch day after clock sync W send() { view offset differs from current offset }`() {
        // Device crash at 2026-11-04 00:01 UTC; the process started two minutes earlier.
        val crashTimeMs = 1_793_750_460_000L
        val timeSinceAppStartMs = 120_000L
        fakeDatadogContext = fakeDatadogContext.copy(
            time = fakeDatadogContext.time.copy(serverTimeOffsetMs = 120_000L),
            featuresContext = mapOf(
                Feature.RUM_FEATURE_NAME to fakeRumContext.copy(viewTimestampOffset = 0L).toMap()
            )
        )
        whenever(mockCall.execute()) doReturn response(202)

        val result = checkNotNull(
            testedBeacon.send(
                { fakeDatadogContext },
                "java.lang.IllegalStateException",
                "boom",
                crashTimeMs,
                timeSinceAppStartMs
            )
        )

        val body = argumentCaptor<Request>().run {
            verify(mockCallFactory).newCall(capture())
            JSONObject(Buffer().also { firstValue.body!!.writeTo(it) }.readUtf8())
        }
        assertThat(result.crashDate).isEqualTo(crashTimeMs)
        assertThat(body.getLong("date")).isEqualTo(result.crashDate)
        assertThat(body.getLong("launch_time")).isEqualTo(crashTimeMs - timeSinceAppStartMs)
    }

    @Test
    fun `M freeze the fallback date W send() { no active view }`() {
        fakeDatadogContext = fakeDatadogContext.copy(
            time = fakeDatadogContext.time.copy(serverTimeOffsetMs = 120_000L),
            featuresContext = mapOf(
                Feature.RUM_FEATURE_NAME to fakeRumContext.copy(viewId = null).toMap()
            )
        )
        whenever(mockCall.execute()) doReturn response(202)

        val result = checkNotNull(
            testedBeacon.send({ fakeDatadogContext }, "java.lang.IllegalStateException", "boom", 1_000_000L, 4_000L)
        )

        assertThat(result.crashDate).isEqualTo(1_120_000L)
    }

    @Test
    fun `M keep the dispatched date W send() { request times out }`() {
        val release = CountDownLatch(1)
        configuration.timeoutMs = 500
        whenever(mockCall.execute()) doAnswer {
            release.await(5, TimeUnit.SECONDS)
            response(202)
        }

        val result = try {
            checkNotNull(
                testedBeacon.send(
                    { fakeDatadogContext },
                    "java.lang.IllegalStateException",
                    "boom",
                    1_000_000L,
                    4_000L
                )
            )
        } finally {
            release.countDown()
        }

        assertThat(result.beacon.status).isEqualTo(CrashBeacon.STATUS_TIMEOUT)
        assertThat(result.crashDate).isEqualTo(1_000_000L + fakeRumContext.viewTimestampOffset)
        verify(mockCallFactory).newCall(any())
    }

    @Test
    fun `M send nothing after the wait expires W send() { context ignores interruption }`() {
        val release = CountDownLatch(1)
        configuration.timeoutMs = 500
        val result = send(readContext = {
            while (release.count > 0) {
                try {
                    release.await()
                } catch (_: InterruptedException) {
                    // Simulate an SDK context read which cannot be cancelled.
                }
            }
            fakeDatadogContext
        })
        release.countDown()
        executor.shutdown()
        assertThat(executor.awaitTermination(2, TimeUnit.SECONDS)).isTrue

        assertThat(result.status).isEqualTo(CrashBeacon.STATUS_TIMEOUT)
        verify(mockCallFactory, never()).newCall(any())
    }

    // region Internal

    @Test
    fun `M stop before HTTP W send() { dispatch date cannot be persisted }`() {
        val result = checkNotNull(
            testedBeacon.send(
                { fakeDatadogContext }, "SIGSEGV", "native crash", 5_000L, 4_000L,
                onDispatch = { throw IOException("disk full") }
            )
        )

        assertThat(result.beacon.status).isEqualTo(CrashBeacon.STATUS_FAILED)
        assertThat(result.crashDate).isNull()
        verify(mockCallFactory, never()).newCall(any())
    }

    @Test
    fun `M stop before HTTP W send() { dispatch persistence outlives native wait }`() {
        val release = CountDownLatch(1)
        val result = try {
            checkNotNull(
                testedBeacon.send(
                    { fakeDatadogContext }, "SIGSEGV", "native crash", 5_000L, 4_000L,
                    onDispatch = {
                        while (release.count > 0) {
                            try {
                                release.await()
                            } catch (_: InterruptedException) {
                                // Disk IO may outlive interruption.
                            }
                        }
                    },
                    waitTimeoutMs = 100L
                )
            )
        } finally {
            release.countDown()
        }
        executor.shutdown()
        assertThat(executor.awaitTermination(2, TimeUnit.SECONDS)).isTrue

        assertThat(result.beacon.status).isEqualTo(CrashBeacon.STATUS_TIMEOUT)
        assertThat(result.crashDate).isNull()
        verify(mockCallFactory, never()).newCall(any())
    }

    @Test
    fun `M use the frozen native budget W send() { remote timeout changed }`() {
        configuration.timeoutMs = 2_000
        whenever(mockCall.execute()) doReturn response(202)

        val result = checkNotNull(
            testedBeacon.send(
                { fakeDatadogContext }, "SIGSEGV", "native crash", 5_000L, 4_000L,
                waitMarginMs = 250L,
                waitTimeoutMs = 500L
            )
        )

        assertThat(result.beacon.status).isEqualTo(CrashBeacon.STATUS_SENT)
        assertThat(mockCall.timeout().timeoutNanos()).isEqualTo(TimeUnit.MILLISECONDS.toNanos(150L))
    }

    @Test
    fun `M send nothing W send() { native wait already exhausted }`() {
        val result = checkNotNull(
            testedBeacon.send(
                { fakeDatadogContext }, "SIGSEGV", "native crash", 5_000L, 4_000L,
                waitMarginMs = 250L,
                waitTimeoutMs = 200L
            )
        )

        assertThat(result.beacon.status).isEqualTo(CrashBeacon.STATUS_TIMEOUT)
        verify(mockCallFactory, never()).newCall(any())
    }

    private fun send(
        crashTimeMs: Long = 1_000_000L,
        timeSinceAppStartMs: Long = 4_000L,
        readContext: () -> DatadogContext? = { fakeDatadogContext }
    ) = checkNotNull(
        testedBeacon.send(
            datadogContext = readContext,
            crashType = IllegalStateException::class.java.canonicalName!!,
            message = "boom",
            crashTimeMs = crashTimeMs,
            timeSinceAppStartMs = timeSinceAppStartMs
        )
    ).beacon

    private fun assertSkipped() {
        val result = send()

        assertThat(result.status).isEqualTo(CrashBeacon.STATUS_SKIPPED)
        verify(mockCallFactory, never()).newCall(any())
    }

    private fun response(code: Int): Response = Response.Builder()
        .request(Request.Builder().url("https://rum.example.com/").build())
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("")
        .body("".toResponseBody())
        .build()

    // endregion
}
