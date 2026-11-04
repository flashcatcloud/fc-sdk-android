/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.ndk.internal

import android.app.Activity
import android.app.Application
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class NdkCrashBeaconTest {

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockRumFeature: FeatureScope

    @Mock
    lateinit var mockApplication: Application

    @TempDir
    lateinit var tempDir: File

    private val fakeNative = FakeNative()
    private val sentToRum = mutableListOf<Map<*, *>>()
    private var visible = true
    private val fakeScheduler = FakeScheduler()

    // What RUM answers when asked to arm: null for "never calls back".
    private var rumArm: Pair<Boolean, Int>? = true to 1_000

    private lateinit var testedBeacon: NdkCrashBeacon

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger) doReturn mock<InternalLogger>()
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeature
        whenever(mockRumFeature.sendEvent(any())) doAnswer {
            val event = it.getArgument<Map<*, *>>(0)
            sentToRum += event
            if (event["type"] == NdkCrashBeacon.ARM_EVENT_TYPE) {
                @Suppress("UNCHECKED_CAST")
                val onArm = event["onArm"] as (Boolean, Int) -> Unit
                rumArm?.let { (enabled, timeoutMs) -> onArm(enabled, timeoutMs) }
            }
        }
        testedBeacon = NdkCrashBeacon(
            sdkCore = mockSdkCore,
            directory = tempDir,
            appStartTimeMs = 1_000L,
            native = fakeNative,
            clock = { 5_000L },
            isProcessUserVisible = { visible },
            scheduler = fakeScheduler
        )
    }

    @AfterEach
    fun `tear down`() {
        // The native beacon is one per process: never leave it held for the next test.
        testedBeacon.stop()
    }

    @Test
    fun `M leave nothing behind W start() { native side cannot be prepared }`() {
        fakeNative.installs = false

        assertThat(testedBeacon.start(mockApplication)).isFalse
        assertThat(fakeNative.arms).isEmpty()
        verify(mockApplication, never()).registerActivityLifecycleCallbacks(any())
    }

    @Test
    fun `M arm with RUM's wait W start() { foreground, RUM has beacons on }`() {
        rumArm = true to 1_500

        assertThat(testedBeacon.start(mockApplication)).isTrue

        assertThat(fakeNative.arms.last()).isEqualTo(true to 1_500)
        verify(mockApplication).registerActivityLifecycleCallbacks(testedBeacon)
    }

    @Test
    fun `M disarm W start() { RUM has beacons off }`() {
        rumArm = false to 1_000

        testedBeacon.start(mockApplication)

        assertThat(fakeNative.arms.last().first).isFalse
    }

    @Test
    fun `M stay armed W start() { RUM not registered yet }`() {
        // The beacon thread asks RUM again at the crash; arming must not wait for RUM.
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn null

        testedBeacon.start(mockApplication)

        assertThat(fakeNative.arms.last()).isEqualTo(true to NdkCrashBeacon.DEFAULT_TIMEOUT_MS)
    }

    @Test
    fun `M disarm a moment after start W start() { process with no activity, not visible }`() {
        visible = false
        testedBeacon.start(mockApplication)
        assertThat(fakeNative.arms.last().first).isTrue

        fakeScheduler.runPending()

        assertThat(fakeNative.arms.last().first).isFalse
    }

    @Test
    fun `M stay armed W A starts B then A stops { SDK set up from activity A }`() {
        // A was never seen starting; B starts before A stops, as Android orders them.
        testedBeacon.start(mockApplication)
        fakeScheduler.cancelAll()

        testedBeacon.onActivityStarted(mock())
        testedBeacon.onActivityStopped(mock())
        fakeScheduler.runPending()

        assertThat(fakeNative.arms.last().first).isTrue
    }

    @Test
    fun `M stay armed through a rotation W the activity stops then starts again`() {
        testedBeacon.start(mockApplication)
        fakeScheduler.cancelAll()
        val activity = mock<Activity>()

        testedBeacon.onActivityStopped(activity)
        testedBeacon.onActivityStarted(activity)

        assertThat(fakeScheduler.pending).isEmpty()
        assertThat(fakeNative.arms.last().first).isTrue
    }

    @Test
    fun `M disarm once the app has left, and arm again W HOME then back`() {
        testedBeacon.start(mockApplication)
        fakeScheduler.cancelAll()
        val activity = mock<Activity>()

        testedBeacon.onActivityStopped(activity)
        visible = false
        fakeScheduler.runPending()
        assertThat(fakeNative.arms.last().first).isFalse

        visible = true
        testedBeacon.onActivityStarted(activity)
        assertThat(fakeNative.arms.last().first).isTrue
    }

    @Test
    fun `M stay armed W the system cannot be asked`() {
        val beacon = NdkCrashBeacon(
            mockSdkCore,
            tempDir,
            1_000L,
            fakeNative,
            { 5_000L },
            { throw SecurityException("no") },
            fakeScheduler
        )
        testedBeacon = beacon
        beacon.start(mockApplication)

        fakeScheduler.runPending()

        assertThat(fakeNative.arms.last().first).isTrue
    }

    @Test
    fun `M ask RUM for the beacon and record its id first W send()`() {
        testedBeacon.send(11)

        val event = sentToRum.single { it["type"] == NdkCrashBeacon.SEND_EVENT_TYPE }
        assertThat(event["crashType"]).isEqualTo("SIGSEGV")
        assertThat(event["message"]).isEqualTo("NDK crash detected with signal: SIGSEGV")
        assertThat(event["crashTimeMs"]).isEqualTo(5_000L)
        assertThat(event["timeSinceAppStartMs"]).isEqualTo(4_000L)

        @Suppress("UNCHECKED_CAST")
        (event["onCrashId"] as (String) -> Unit)(CRASH_ID)
        assertThat(File(tempDir, NdkCrashBeacon.RESULT_FILE_NAME).readText()).isEqualTo("$CRASH_ID timeout 0")

        @Suppress("UNCHECKED_CAST")
        (event["onResult"] as (String, String, Long) -> Unit)(CRASH_ID, "sent", 105L)
        assertThat(File(tempDir, NdkCrashBeacon.RESULT_FILE_NAME).readText()).isEqualTo("$CRASH_ID sent 105")
    }

    @Test
    fun `M send once and let the crashed thread go on W a crash wakes the thread`() {
        testedBeacon.start(mockApplication)

        fakeNative.crash(6)

        assertThat(fakeNative.finished.await(5, TimeUnit.SECONDS)).isTrue
        assertThat(sentToRum.filter { it["type"] == NdkCrashBeacon.SEND_EVENT_TYPE }.map { it["crashType"] })
            .containsExactly("SIGABRT")
    }

    @Test
    fun `M serve a later crash too W a crash the process survived`() {
        testedBeacon.start(mockApplication)

        fakeNative.crash(11)
        assertThat(fakeNative.finishes.poll(5, TimeUnit.SECONDS)).isNotNull
        fakeNative.crash(6)
        assertThat(fakeNative.finishes.poll(5, TimeUnit.SECONDS)).isNotNull

        assertThat(sentToRum.filter { it["type"] == NdkCrashBeacon.SEND_EVENT_TYPE }.map { it["crashType"] })
            .containsExactly("SIGSEGV", "SIGABRT")
    }

    @Test
    fun `M refuse a second native beacon W start() { another one is running }`() {
        testedBeacon.start(mockApplication)
        val other = NdkCrashBeacon(mockSdkCore, tempDir, 1_000L, FakeNative(), { 5_000L }, { true }, FakeScheduler())

        assertThat(other.start(mockApplication)).isFalse

        testedBeacon.stop()
        assertThat(other.start(mockApplication)).isTrue
        other.stop()
    }

    @Test
    fun `M end its thread before returning W stop()`() {
        testedBeacon.start(mockApplication)
        val thread = Thread.getAllStackTraces().keys.single { it.name == NdkCrashBeacon.THREAD_NAME }

        testedBeacon.stop()

        assertThat(thread.isAlive).isFalse
    }

    @Test
    fun `M let the crashed thread go on W a crash wakes the thread { RUM throws }`() {
        whenever(mockRumFeature.sendEvent(any())) doAnswer {
            if ((it.getArgument<Map<*, *>>(0))["type"] == NdkCrashBeacon.SEND_EVENT_TYPE) {
                throw OutOfMemoryError("no room left")
            }
        }
        testedBeacon.start(mockApplication)

        fakeNative.crash(11)

        assertThat(fakeNative.finished.await(5, TimeUnit.SECONDS)).isTrue
    }

    @Test
    fun `M end the thread and unregister W stop()`() {
        testedBeacon.start(mockApplication)

        testedBeacon.stop()

        assertThat(fakeNative.uninstalled).isTrue
        verify(mockApplication).unregisterActivityLifecycleCallbacks(testedBeacon)
    }

    @Test
    fun `M name the caught signals W signalName()`() {
        assertThat(listOf(4, 5, 6, 7, 8, 11).map(NdkCrashBeacon::signalName))
            .containsExactly("SIGILL", "SIGTRAP", "SIGABRT", "SIGBUS", "SIGFPE", "SIGSEGV")
        assertThat(NdkCrashBeacon.signalName(31)).isEqualTo("SIGNAL 31")
    }

    private class FakeScheduler : NdkCrashBeacon.Scheduler {
        val pending = mutableListOf<Runnable>()
        override fun schedule(task: Runnable, delayMs: Long) {
            pending += task
        }

        override fun cancel(task: Runnable) {
            pending.remove(task)
        }

        fun cancelAll() = pending.clear()

        fun runPending() {
            val tasks = pending.toList()
            pending.clear()
            tasks.forEach { it.run() }
        }
    }

    private class FakeNative : NdkCrashBeacon.Native {
        var installs = true
        val arms = mutableListOf<Pair<Boolean, Int>>()
        var uninstalled = false
        val finished = CountDownLatch(1)
        val finishes = LinkedBlockingQueue<Unit>()
        private val crashes = LinkedBlockingQueue<Int>()

        fun crash(signal: Int) = crashes.put(signal)

        override fun install() = installs
        override fun uninstall() {
            uninstalled = true
            crashes.put(0)
        }

        override fun arm(armed: Boolean, timeoutMs: Int) {
            arms += armed to timeoutMs
        }

        override fun waitForCrash(): Int = crashes.take()
        override fun finish() {
            finished.countDown()
            finishes.put(Unit)
        }
    }

    private companion object {
        const val CRASH_ID = "6a1f0c2e-3b4d-4e5f-8a9b-0c1d2e3f4a5b"
    }
}
