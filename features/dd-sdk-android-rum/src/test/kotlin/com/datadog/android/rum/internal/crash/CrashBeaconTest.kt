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

    private var remoteEnabled: Boolean? = null
    private var remoteTimeoutMs: Int? = null
    private var inForeground = true

    private val fakeRumContext = RumContext(
        applicationId = "app-id",
        sessionId = "8f2c6b1e-6d55-4f5e-9a51-0c1b8a1e2f3d",
        viewId = "0b6e3c4a-1d2e-4f5a-8b9c-0d1e2f3a4b5c",
        viewName = "CheckoutActivity",
        viewUrl = "com.example.CheckoutActivity",
        sessionState = RumSessionScope.State.TRACKED
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
            remoteEnabled = { remoteEnabled },
            remoteTimeoutMs = { remoteTimeoutMs },
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
        val offset = fakeDatadogContext.time.serverTimeOffsetMs

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
        remoteTimeoutMs = 200

        val start = System.nanoTime()
        val result = send()
        val waitedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
        release.countDown()

        assertThat(result.status).isEqualTo(CrashBeacon.STATUS_TIMEOUT)
        assertThat(waitedMs).isLessThan(1_000L)
    }

    @Test
    fun `M skip it W send() { app in the background }`() {
        inForeground = false

        assertSkipped()
    }

    @Test
    fun `M skip it W send() { console turned beacons off }`() {
        remoteEnabled = false

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
            throwable = IllegalStateException("again"),
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
        remoteTimeoutMs = 200

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
    fun `M draw one crash id per process W crashId`() {
        assertThat(testedBeacon.crashId).isEqualTo(testedBeacon.crashId)
        assertThat(testedBeacon.crashId).matches("^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$")
    }

    // region Internal

    private fun send(
        crashTimeMs: Long = 1_000_000L,
        timeSinceAppStartMs: Long = 4_000L,
        readContext: () -> DatadogContext? = { fakeDatadogContext }
    ) = checkNotNull(
        testedBeacon.send(
            datadogContext = readContext,
            throwable = IllegalStateException("boom"),
            message = "boom",
            crashTimeMs = crashTimeMs,
            timeSinceAppStartMs = timeSinceAppStartMs
        )
    )

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
