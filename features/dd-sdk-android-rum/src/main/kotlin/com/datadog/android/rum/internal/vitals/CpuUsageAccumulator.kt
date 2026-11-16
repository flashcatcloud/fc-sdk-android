/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.vitals

internal data class CpuUsageSample(val process: Double, val mainThread: Double, val time: Double)

internal data class CpuUsageValues(
    val average: Double,
    val max: Double,
    val mainThreadAverage: Double,
    val mainThreadMax: Double
)

/** Per-view foreground intervals. Failed reads and background transitions break the baseline. */
internal class CpuUsageAccumulator(private val processorCount: Int) {
    private var previous: CpuUsageSample? = null
    private var process = 0.0
    private var mainThread = 0.0
    private var duration = 0.0
    private var peakProcess = 0.0
    private var peakMain = 0.0
    private var peakDuration = 0.0
    private var processMax = 0.0
    private var mainMax = 0.0

    @Synchronized
    fun add(sample: CpuUsageSample?) {
        val last = previous
        previous = sample
        if (sample == null || last == null) {
            resetPeakInterval()
            return
        }
        val time = sample.time - last.time
        val cpu = sample.process - last.process
        val main = sample.mainThread - last.mainThread
        if (!time.isFinite() || !cpu.isFinite() || !main.isFinite() || time <= 0 || cpu < 0 || main < 0) {
            resetPeakInterval()
            return
        }
        duration += time
        process += cpu
        mainThread += main
        peakDuration += time
        peakProcess += cpu
        peakMain += main
        if (peakDuration >= MIN_PEAK_INTERVAL) {
            processMax = maxOf(processMax, percent(peakProcess, peakDuration * processorCount.coerceAtLeast(1)))
            mainMax = maxOf(mainMax, percent(peakMain, peakDuration))
            resetPeakInterval()
        }
    }

    @Synchronized
    fun values(): CpuUsageValues? {
        if (duration < MIN_FOREGROUND_TIME) return null
        val average = percent(process, duration * processorCount.coerceAtLeast(1))
        val mainAverage = percent(mainThread, duration)
        // Like iOS, include the trailing short interval in the average and use it as a peak lower bound.
        return CpuUsageValues(average, maxOf(processMax, average), mainAverage, maxOf(mainMax, mainAverage))
    }

    private fun resetPeakInterval() {
        peakDuration = 0.0
        peakProcess = 0.0
        peakMain = 0.0
    }

    private fun percent(cpu: Double, capacity: Double): Double = (cpu / capacity * 100).coerceIn(0.0, 100.0)

    private companion object {
        const val MIN_FOREGROUND_TIME = 0.9
        const val MIN_PEAK_INTERVAL = 0.25
    }
}
