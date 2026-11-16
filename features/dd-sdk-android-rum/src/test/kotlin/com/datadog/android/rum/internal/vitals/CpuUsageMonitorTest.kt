/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.vitals

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class CpuUsageMonitorTest {
    @Test
    fun `closes foreground intervals and excludes CPU accumulated in background`() {
        var sample = CpuUsageSample(0.0, 0.0, 0.0)
        val monitor = CpuUsageMonitor({ sample }, 2, true)
        val view = monitor.registerUsage()
        sample = CpuUsageSample(0.5, 0.25, 0.5)
        monitor.setForeground(false)
        sample = CpuUsageSample(50.0, 25.0, 100.0)
        monitor.sample()
        monitor.setForeground(true)
        sample = CpuUsageSample(50.5, 25.25, 100.5)
        monitor.unregisterUsage(view)
        assertThat(view.values()?.average).isEqualTo(50.0)
        assertThat(view.values()?.mainThreadAverage).isEqualTo(50.0)
        sample = CpuUsageSample(100.0, 100.0, 200.0)
        monitor.sample()
        assertThat(view.values()?.average).isEqualTo(50.0)
    }

    @Test
    fun `new views use a fresh baseline and do not inherit preceding view usage`() {
        var sample = CpuUsageSample(0.0, 0.0, 0.0)
        val monitor = CpuUsageMonitor({ sample }, 4, true)
        val first = monitor.registerUsage()
        sample = CpuUsageSample(4.0, 1.0, 1.0)
        monitor.unregisterUsage(first)
        val second = monitor.registerUsage()
        sample = CpuUsageSample(4.0, 1.0, 2.0)
        monitor.sample()
        assertThat(first.values()?.average).isEqualTo(100.0)
        assertThat(second.values()?.average).isEqualTo(0.0)
        monitor.stop()
        sample = CpuUsageSample(100.0, 100.0, 3.0)
        monitor.sample()
        assertThat(second.values()?.average).isEqualTo(0.0)
    }

    @Test
    fun `failed sample is a gap and legacy tick listeners remain independent`() {
        var sample: CpuUsageSample? = CpuUsageSample(0.0, 0.0, 0.0)
        val monitor = CpuUsageMonitor({ sample }, 2, true)
        val view = monitor.registerUsage()
        sample = null
        monitor.sample()
        sample = CpuUsageSample(100.0, 50.0, 10.0)
        monitor.sample()
        assertThat(view.values()).isNull()
        monitor.onNewSample(123.0)
        assertThat(monitor.getLastSample()).isEqualTo(123.0)
    }
}
