/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.ndk.internal

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.internal.persistence.file.existsSafe
import com.datadog.android.core.internal.persistence.file.listFilesSafe
import com.datadog.android.core.internal.persistence.file.readTextSafe
import com.datadog.android.core.internal.utils.executeSafe
import com.google.gson.JsonObject
import java.io.File
import java.util.Locale
import java.util.concurrent.ExecutorService

@Suppress("TooManyFunctions", "LongParameterList")
internal class DatadogNdkCrashHandler(
    storageDir: File,
    private val dataPersistenceExecutorService: ExecutorService,
    private val ndkCrashLogDeserializer: Deserializer<String, NdkCrashLog>,
    private val internalLogger: InternalLogger,
    private val lastRumViewEventProvider: () -> JsonObject?,
    internal val nativeCrashSourceType: String = "ndk"
) : NdkCrashHandler {

    internal val ndkCrashDataDirectory: File = getNdkGrantedDir(storageDir)

    internal var lastRumViewEvent: JsonObject? = null
    internal var lastNdkCrashLog: NdkCrashLog? = null

    // FLASHCAT FORK - the crash beacon of that crash, written beside it when the app turned beacons
    // on: the id the beacon counted the crash under, and what became of it.
    internal var lastCrashBeacon: CrashBeaconRecord? = null

    // region NdkCrashHandler

    override fun prepareData() {
        dataPersistenceExecutorService.executeSafe("NDK crash check", internalLogger) {
            readCrashData()
        }
    }

    override fun handleNdkCrash(sdkCore: FeatureSdkCore) {
        dataPersistenceExecutorService.executeSafe("NDK crash report ", internalLogger) {
            checkAndHandleNdkCrashReport(sdkCore)
        }
    }

    // endregion

    // region Internal

    @Suppress("NestedBlockDepth")
    @WorkerThread
    private fun readCrashData() {
        if (!ndkCrashDataDirectory.existsSafe(internalLogger)) {
            return
        }
        try {
            lastRumViewEvent = lastRumViewEventProvider()

            ndkCrashDataDirectory.listFilesSafe(internalLogger)?.forEach { file ->
                when (file.name) {
                    // TODO RUM-639 Data from NDK should be also encrypted
                    CRASH_DATA_FILE_NAME ->
                        lastNdkCrashLog =
                            file.readTextSafe(internalLogger = internalLogger)?.let {
                                ndkCrashLogDeserializer.deserialize(it)
                            }

                    CRASH_BEACON_FILE_NAME ->
                        lastCrashBeacon = file.readTextSafe(internalLogger = internalLogger)
                            ?.let { CrashBeaconRecord.parse(it) }
                }
            }
        } catch (e: SecurityException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { ERROR_READ_NDK_DIR },
                e
            )
        } finally {
            clearCrashLog()
        }
    }

    @WorkerThread
    private fun checkAndHandleNdkCrashReport(sdkCore: FeatureSdkCore) {
        if (lastNdkCrashLog != null) {
            handleNdkCrashLog(
                sdkCore,
                lastNdkCrashLog,
                lastRumViewEvent
            )
            clearAllReferences()
        }
    }

    private fun clearAllReferences() {
        lastRumViewEvent = null
        lastNdkCrashLog = null
        lastCrashBeacon = null
    }

    @WorkerThread
    private fun handleNdkCrashLog(
        sdkCore: FeatureSdkCore,
        ndkCrashLog: NdkCrashLog?,
        lastViewEvent: JsonObject?
    ) {
        if (ndkCrashLog == null) {
            return
        }
        val errorLogMessage = LOG_CRASH_MSG.format(Locale.US, ndkCrashLog.signalName)

        if (lastViewEvent != null) {
            sendCrashRumEvent(
                sdkCore,
                errorLogMessage,
                ndkCrashLog,
                lastViewEvent
            )
        }
    }

    @Suppress("StringLiteralDuplication")
    @WorkerThread
    private fun sendCrashRumEvent(
        sdkCore: FeatureSdkCore,
        errorLogMessage: String,
        ndkCrashLog: NdkCrashLog,
        lastViewEvent: JsonObject
    ) {
        val rumFeature = sdkCore.getFeature(Feature.RUM_FEATURE_NAME)
        if (rumFeature != null) {
            rumFeature.sendEvent(
                mapOf(
                    "type" to "ndk_crash",
                    "sourceType" to nativeCrashSourceType,
                    "timestamp" to ndkCrashLog.timestamp,
                    "timeSinceAppStartMs" to ndkCrashLog.timeSinceAppStartMs,
                    "signalName" to ndkCrashLog.signalName,
                    "stacktrace" to ndkCrashLog.stacktrace,
                    "message" to errorLogMessage,
                    "lastViewEvent" to lastViewEvent
                ) + lastCrashBeacon?.takeIf { it.belongsTo(ndkCrashLog) }?.let {
                    mapOf(
                        "crashId" to it.crashId,
                        "beaconStatus" to it.status,
                        "beaconDuration" to it.durationMs,
                        "crashDate" to it.date.takeIf { date -> date > 0L }
                    )
                }.orEmpty()
            )
        } else {
            internalLogger.log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.USER,
                { INFO_RUM_FEATURE_NOT_REGISTERED }
            )
        }
    }

    @SuppressWarnings("TooGenericExceptionCaught")
    private fun clearCrashLog() {
        if (ndkCrashDataDirectory.existsSafe(internalLogger)) {
            try {
                ndkCrashDataDirectory.listFilesSafe(internalLogger)
                    ?.forEach { it.deleteRecursively() }
            } catch (e: Throwable) {
                internalLogger.log(
                    InternalLogger.Level.ERROR,
                    listOf(
                        InternalLogger.Target.MAINTAINER,
                        InternalLogger.Target.TELEMETRY
                    ),
                    {
                        "Unable to clear the NDK crash report file:" +
                            " ${ndkCrashDataDirectory.absolutePath}"
                    },
                    e
                )
            }
        }
    }

    // endregion

    companion object {

        private const val RUM_VIEW_EVENT_FILE_NAME = "last_view_event"
        internal const val CRASH_DATA_FILE_NAME = "crash_log"
        internal const val CRASH_BEACON_FILE_NAME = "crash_beacon"
        internal const val USER_INFO_FILE_NAME = "user_information"
        internal const val NETWORK_INFO_FILE_NAME = "network_information"

        internal const val LOG_CRASH_MSG = "NDK crash detected with signal: %s"
        internal const val ERROR_READ_NDK_DIR = "Error while trying to read the NDK crash directory"

        internal const val INFO_RUM_FEATURE_NOT_REGISTERED =
            "RUM feature is not registered, won't report NDK crash info as RUM error."

        private const val STORAGE_VERSION = 2

        internal const val NDK_CRASH_REPORTS_FOLDER_NAME = "ndk_crash_reports_v$STORAGE_VERSION"
        private const val NDK_CRASH_REPORTS_PENDING_FOLDER_NAME =
            "ndk_crash_reports_intermediary_v$STORAGE_VERSION"

        private fun getNdkGrantedDir(storageDir: File): File {
            return File(storageDir, NDK_CRASH_REPORTS_FOLDER_NAME)
        }

        private fun getNdkPendingDir(storageDir: File): File {
            return File(storageDir, NDK_CRASH_REPORTS_PENDING_FOLDER_NAME)
        }

        @Deprecated(
            "We will still process this path to check file from the old SDK" +
                " versions, but don't use it anymore for writing."
        )
        internal fun getLastViewEventFile(storageDir: File): File {
            return File(getNdkGrantedDir(storageDir), RUM_VIEW_EVENT_FILE_NAME)
        }

        internal fun getPendingNetworkInfoFile(storageDir: File): File {
            return File(getNdkPendingDir(storageDir), NETWORK_INFO_FILE_NAME)
        }

        internal fun getGrantedNetworkInfoFile(storageDir: File): File {
            return File(getNdkGrantedDir(storageDir), NETWORK_INFO_FILE_NAME)
        }

        internal fun getPendingUserInfoFile(storageDir: File): File {
            return File(getNdkPendingDir(storageDir), USER_INFO_FILE_NAME)
        }

        internal fun getGrantedUserInfoFile(storageDir: File): File {
            return File(getNdkGrantedDir(storageDir), USER_INFO_FILE_NAME)
        }
    }
}

/**
 * FLASHCAT FORK - what the beacon thread of a native crash wrote beside its report:
 * `<crash id> <status> <duration ms> <crash time ms> <beacon date>`, the date 0 when the beacon
 * was never dispatched.
 */
internal data class CrashBeaconRecord(
    val crashId: String,
    val status: String,
    val durationMs: Long,
    val crashTimeMs: Long,
    val date: Long
) {
    /**
     * Whether this record was written for that crash report: a process that survived a crash and
     * crashed again later, unarmed, leaves the first crash's record beside the second's report.
     */
    fun belongsTo(crashLog: NdkCrashLog): Boolean =
        crashLog.timestamp == crashTimeMs

    internal companion object {
        private val UUID_PATTERN = Regex("^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$")

        /** Null for anything else: the report then keeps an id of its own, as before. */
        fun parse(text: String): CrashBeaconRecord? {
            val parts = text.trim().split(' ')
            if (parts.size != 5 || !UUID_PATTERN.matches(parts[0])) return null
            val duration = parts[2].toLongOrNull() ?: return null
            val crashTimeMs = parts[3].toLongOrNull() ?: return null
            val date = parts[4].toLongOrNull() ?: return null
            return CrashBeaconRecord(parts[0], parts[1], duration, crashTimeMs, date)
        }
    }
}
