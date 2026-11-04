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
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

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
    private val isProcessInForeground: () -> Boolean = ::processImportanceIsForeground
) : Application.ActivityLifecycleCallbacks {

    private val startedActivities = AtomicInteger(0)

    @Volatile
    private var inForeground = false
    private var application: Application? = null
    private var thread: Thread? = null

    /** Returns false, leaving nothing behind, when the native side cannot be prepared. */
    fun start(application: Application?): Boolean {
        if (!native.install()) return false
        thread = Thread(::waitForCrash, THREAD_NAME).apply {
            isDaemon = true
            start()
        }
        inForeground = safeIsProcessInForeground()
        this.application = application
        application?.registerActivityLifecycleCallbacks(this)
        rearm()
        return true
    }

    fun stop() {
        application?.unregisterActivityLifecycleCallbacks(this)
        application = null
        // Closing the pipes ends the thread's wait.
        native.uninstall()
        thread = null
    }

    // region Arming

    /**
     * Whether a crash now wakes the beacon thread. Kept in a flag the signal handler can read, since
     * it can ask nothing itself: the app is in the foreground, and RUM has beacons on.
     */
    internal fun rearm() {
        var enabled = false
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
        native.arm(enabled && inForeground, timeoutMs)
    }

    override fun onActivityStarted(activity: Activity) {
        if (startedActivities.incrementAndGet() == 1) {
            inForeground = true
            rearm()
        }
    }

    override fun onActivityStopped(activity: Activity) {
        // Floored at zero: an activity started before this was registered stops without having
        // been counted.
        if (startedActivities.get() > 0) startedActivities.decrementAndGet()
        if (startedActivities.get() == 0) {
            inForeground = false
            rearm()
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    // endregion

    // region Beacon thread

    private fun waitForCrash() {
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

    private fun writeResult(crashId: String, status: String, durationMs: Long) {
        File(directory, RESULT_FILE_NAME).writeText("$crashId $status $durationMs")
    }

    // endregion

    private fun sendToRum(event: Map<String, Any?>) {
        sdkCore.getFeature(Feature.RUM_FEATURE_NAME)?.sendEvent(event)
    }

    private fun safeIsProcessInForeground(): Boolean = try {
        isProcessInForeground()
    } catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
        false
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

        private fun processImportanceIsForeground(): Boolean {
            val info = ActivityManager.RunningAppProcessInfo()
            ActivityManager.getMyMemoryState(info)
            return info.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
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
