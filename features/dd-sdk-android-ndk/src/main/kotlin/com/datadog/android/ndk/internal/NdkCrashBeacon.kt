/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.ndk.internal

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * FLASHCAT FORK - the crash beacon of native crashes, on the Java side.
 *
 * The signal handler wakes [thread] once the crash report is written and waits for it within the
 * timeout (see `crash-beacon.h`). The thread then has RUM send the beacon, the same one a JVM crash
 * sends, and lets the crashed thread go on.
 *
 * Exists only when the app turned the beacon on: otherwise nothing here is created, and native
 * crashes are handled exactly as before.
 *
 * Before the beacon is sent, the crash id it is sent under is written next to the crash report, so
 * the report read on the next launch carries the same id: a beacon that reached the intake and a
 * report under another id would count one crash twice.
 */
@Suppress("TooManyFunctions")
internal class NdkCrashBeacon(
    private val sdkCore: FeatureSdkCore,
    /** The NDK crash reports directory, where the crash report is written. */
    private val directory: File,
    /** The process start, on the clock the crash report is timed with. */
    private val appStartTimeMs: Long,
    private val native: Native = JniNative,
    private val clock: () -> Long = System::currentTimeMillis,
    private val isProcessUserVisible: () -> Boolean = ::processImportanceIsUserVisible,
    private val scheduler: Scheduler = MainThreadScheduler()
) : Application.ActivityLifecycleCallbacks {

    /**
     * A hint, not the rule: the beacon thread applies the real foreground rule at the crash, the
     * one JVM crashes use. The hint only spares a crash that is certainly in the background the
     * round trip to that thread, so it errs towards armed - a missed foreground beacon is a lost
     * crash count, a needless round trip costs a few milliseconds.
     */
    @Volatile
    private var foregroundHint = true
    private var application: Application? = null
    private var thread: Thread? = null

    /** Looks again once the app may have left the foreground. */
    private val recheck = Runnable {
        foregroundHint = safeIsProcessUserVisible()
        rearm()
    }

    /**
     * Returns false, leaving nothing behind, when the beacon cannot be set up: the native side
     * cannot be prepared, no thread can be started, or another SDK instance already has the
     * process's native beacon (the signal handler is one per process).
     */
    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    fun start(application: Application?): Boolean {
        if (!ACTIVE.compareAndSet(null, this)) return false
        if (!native.install()) {
            ACTIVE.set(null)
            return false
        }
        thread = try {
            Thread(::waitForCrashes, THREAD_NAME).apply {
                isDaemon = true
                start()
            }
        } catch (e: Throwable) {
            // Out of threads or memory: no beacon, and nothing thrown into the app's init.
            native.uninstall()
            ACTIVE.set(null)
            return false
        }
        foregroundHint = true
        this.application = application
        application?.registerActivityLifecycleCallbacks(this)
        rearm()
        // A process with no activity (a service, a receiver) is never told it is in the background.
        scheduler.schedule(recheck, RECHECK_DELAY_MS)
        return true
    }

    fun stop() {
        application?.unregisterActivityLifecycleCallbacks(this)
        application = null
        scheduler.cancel(recheck)
        // Wakes the thread with no crash, which ends it. Waiting for it before letting another
        // beacon start keeps the two from taking each other's wake-up.
        native.uninstall()
        try {
            thread?.join(STOP_WAIT_MS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        thread = null
        ACTIVE.compareAndSet(this, null)
    }

    // region Arming

    /**
     * Whether a crash now wakes the beacon thread, kept in a flag the signal handler can read since
     * it can ask nothing itself. Armed unless the app is certainly in the background or RUM says
     * beacons are off; without an answer from RUM (not registered yet), armed: the beacon thread
     * still asks RUM at the crash. Synchronized, so two callers cannot apply stale states out of
     * order.
     */
    @Synchronized
    internal fun rearm() {
        var enabled = true
        var timeoutMs = DEFAULT_TIMEOUT_MS
        sendToRum(
            mapOf(
                "type" to ARM_EVENT_TYPE,
                "onArm" to { rumEnabled: Boolean, rumTimeoutMs: Int ->
                    enabled = rumEnabled
                    timeoutMs = rumTimeoutMs
                }
            )
        )
        native.arm(enabled && foregroundHint, timeoutMs)
    }

    override fun onActivityStarted(activity: Activity) {
        scheduler.cancel(recheck)
        foregroundHint = true
        rearm()
    }

    /**
     * No counting: an app that set the SDK up from an activity never saw that activity start, and
     * a count would go wrong. Whether the app really left is asked of the system a moment later,
     * once A-starts-B and rotations have settled, and any start in between cancels the question.
     */
    override fun onActivityStopped(activity: Activity) {
        scheduler.cancel(recheck)
        scheduler.schedule(recheck, RECHECK_DELAY_MS)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    // endregion

    // region Beacon thread

    /**
     * Serves crashes until the beacon is stopped. A crash normally ends the process, but one the
     * previous handler recovers from must not leave a later crash waiting for a thread that is gone.
     */
    private fun waitForCrashes() {
        while (true) {
            val signal = native.waitForCrash()
            if (signal <= 0) return
            try {
                send(signal)
            } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
                sdkCore.internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.MAINTAINER,
                    { SEND_FAILED_MESSAGE },
                    e
                )
            } finally {
                native.finish()
            }
        }
    }

    internal fun send(signal: Int) {
        val crashTimeMs = clock()
        val signalName = signalName(signal)
        sendToRum(
            mapOf(
                "type" to SEND_EVENT_TYPE,
                "crashType" to signalName,
                "message" to CRASH_MESSAGE.format(signalName),
                "crashTimeMs" to crashTimeMs,
                "timeSinceAppStartMs" to crashTimeMs - appStartTimeMs,
                // Written before the beacon goes out, as an unanswered one, and rewritten with
                // the outcome when there is one.
                "onCrashId" to { crashId: String -> writeResult(crashId, STATUS_TIMEOUT, 0L) },
                "onResult" to { crashId: String, status: String, durationMs: Long ->
                    writeResult(crashId, status, durationMs)
                }
            )
        )
    }

    /**
     * Replaces the file whole: the process can be killed at any moment of a write, and a truncated
     * file would lose the id the beacon was sent under.
     */
    private fun writeResult(crashId: String, status: String, durationMs: Long) {
        val partial = File(directory, "$RESULT_FILE_NAME.partial")
        partial.writeText("$crashId $status $durationMs")
        check(partial.renameTo(File(directory, RESULT_FILE_NAME))) { "Unable to record the crash beacon" }
    }

    // endregion

    private fun sendToRum(event: Map<String, Any?>) {
        sdkCore.getFeature(Feature.RUM_FEATURE_NAME)?.sendEvent(event)
    }

    private fun safeIsProcessUserVisible(): Boolean = try {
        isProcessUserVisible()
    } catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
        // Unknown: stay armed, the beacon thread decides at the crash.
        true
    }

    /** Delayed work on the main thread, behind an interface so the arming can be tested. */
    internal interface Scheduler {
        fun schedule(task: Runnable, delayMs: Long)
        fun cancel(task: Runnable)
    }

    private class MainThreadScheduler : Scheduler {
        private val handler by lazy { Handler(Looper.getMainLooper()) }
        override fun schedule(task: Runnable, delayMs: Long) {
            handler.postDelayed(task, delayMs)
        }

        override fun cancel(task: Runnable) {
            handler.removeCallbacks(task)
        }
    }

    /** The native side, behind an interface so the Java side can be tested without it. */
    internal interface Native {
        fun install(): Boolean
        fun uninstall()
        fun arm(armed: Boolean, timeoutMs: Int)
        fun waitForCrash(): Int
        fun finish()
    }

    private object JniNative : Native {
        override fun install() = installNative()
        override fun uninstall() = uninstallNative()
        override fun arm(armed: Boolean, timeoutMs: Int) = armNative(armed, timeoutMs)
        override fun waitForCrash() = waitForCrashNative()
        override fun finish() = finishNative()
    }

    internal companion object {
        internal const val THREAD_NAME = "rum-ndk-crash-beacon"
        internal const val ARM_EVENT_TYPE = "ndk_crash_beacon_arm"
        internal const val SEND_EVENT_TYPE = "ndk_crash_beacon"
        internal const val RESULT_FILE_NAME = "crash_beacon"
        internal const val STATUS_TIMEOUT = "timeout"
        internal const val DEFAULT_TIMEOUT_MS = 1_000
        private const val STOP_WAIT_MS = 500L

        // Long enough for A-starts-B and a rotation to deliver the next start.
        private const val RECHECK_DELAY_MS = 1_000L

        /** The native beacon is one per process, like the signal handler it hooks into. */
        private val ACTIVE = AtomicReference<NdkCrashBeacon?>(null)

        // The message the late crash report carries, see DatadogNdkCrashHandler.LOG_CRASH_MSG.
        private const val CRASH_MESSAGE = "NDK crash detected with signal: %s"
        private const val SEND_FAILED_MESSAGE = "Unable to send the crash beacon of a native crash."

        // Linux signal numbers, the same on every Android ABI for the signals the handler catches.
        private val SIGNAL_NAMES = mapOf(
            4 to "SIGILL",
            5 to "SIGTRAP",
            6 to "SIGABRT",
            7 to "SIGBUS",
            8 to "SIGFPE",
            11 to "SIGSEGV"
        )

        internal fun signalName(signal: Int): String = SIGNAL_NAMES[signal] ?: "SIGNAL $signal"

        /** The same rule as JVM crash beacons: in front, or visible in split screen or PiP. */
        private fun processImportanceIsUserVisible(): Boolean {
            val info = ActivityManager.RunningAppProcessInfo()
            ActivityManager.getMyMemoryState(info)
            return info.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
                info.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
        }

        @JvmStatic
        private external fun installNative(): Boolean

        @JvmStatic
        private external fun uninstallNative()

        @JvmStatic
        private external fun armNative(armed: Boolean, timeoutMs: Int)

        @JvmStatic
        private external fun waitForCrashNative(): Int

        @JvmStatic
        private external fun finishNative()
    }
}
