/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.vitals

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.utils.scheduleSafe
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** Adds percentage sampling to the existing tick monitor without changing its contract or values. */
internal class CpuUsageMonitor(
    private val readSample: () -> CpuUsageSample?,
    private val processorCount: Int,
    private var foreground: Boolean,
    private val legacy: VitalMonitor = AggregatingVitalMonitor()
) : VitalMonitor by legacy {
    private val views = mutableSetOf<CpuUsageAccumulator>()
    private var stopped = false

    @Synchronized
    fun registerUsage(): CpuUsageAccumulator {
        val info = CpuUsageAccumulator(processorCount)
        if (foreground) info.add(readSample())
        views.add(info)
        return info
    }

    @Synchronized
    fun unregisterUsage(info: CpuUsageAccumulator) {
        if (foreground) info.add(readSample())
        views.remove(info)
    }

    @Synchronized
    fun setForeground(active: Boolean) {
        if (foreground == active || stopped) return
        // Close the foreground interval before pausing, then establish a fresh baseline on return.
        if (foreground) sample()
        views.forEach { it.add(null) }
        foreground = active
        if (foreground) sample()
    }

    @Synchronized
    fun sample() {
        if (!foreground || stopped || views.isEmpty()) return
        val value = readSample()
        views.forEach { it.add(value) }
    }

    @Synchronized
    fun stop() {
        stopped = true
        views.clear()
    }

    fun start(executor: ScheduledExecutorService, periodMs: Long, logger: InternalLogger) {
        val task = object : Runnable {
            override fun run() {
                synchronized(this@CpuUsageMonitor) {
                    if (stopped) return
                    sample()
                    executor.scheduleSafe("CPU usage monitoring", periodMs, TimeUnit.MILLISECONDS, logger, this)
                }
            }
        }
        executor.scheduleSafe("CPU usage monitoring", periodMs, TimeUnit.MILLISECONDS, logger, task)
    }
}
