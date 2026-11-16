/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.vitals

import android.os.Process
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.persistence.file.readTextSafe
import java.io.File

/** Process and main-thread user + system CPU seconds, independent of the legacy user ticks. */
internal class CpuUsageReader(
    private val internalLogger: InternalLogger,
    private val processStat: File = File("/proc/self/stat"),
    private val mainThreadStat: File = File("/proc/self/task/${Process.myPid()}/stat"),
    private val ticksPerSecond: Long = runCatching { Os.sysconf(OsConstants._SC_CLK_TCK) }.getOrDefault(-1),
    private val clock: () -> Double = { SystemClock.elapsedRealtimeNanos() / NANOS_PER_SECOND }
) {
    fun read(): CpuUsageSample? {
        if (ticksPerSecond <= 0) return null
        val process = processStat.readTextSafe(internalLogger = internalLogger)?.let(::cpuTicks) ?: return null
        val main = mainThreadStat.readTextSafe(internalLogger = internalLogger)?.let(::cpuTicks) ?: return null
        return CpuUsageSample(process / ticksPerSecond, main / ticksPerSecond, clock())
    }

    internal companion object {
        private const val NANOS_PER_SECOND = 1_000_000_000.0
        private const val USER_TIME_INDEX = 11
        private const val SYSTEM_TIME_INDEX = 12

        // comm (field 2) may contain spaces and parentheses. Fields after its LAST ')' start at state (3).
        fun cpuTicks(stat: String): Double? {
            val end = stat.lastIndexOf(')')
            if (end < 0) return null
            val fields = stat.substring(end + 1).trim().split(Regex("\\s+"))
            val user = fields.getOrNull(USER_TIME_INDEX)?.toDoubleOrNull() ?: return null
            val system = fields.getOrNull(SYSTEM_TIME_INDEX)?.toDoubleOrNull() ?: return null
            return (user + system).takeIf { user >= 0 && system >= 0 && it.isFinite() }
        }
    }
}
