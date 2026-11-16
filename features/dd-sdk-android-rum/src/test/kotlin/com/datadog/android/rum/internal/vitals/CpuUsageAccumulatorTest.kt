/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.vitals

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset.offset
import org.junit.jupiter.api.Test

internal class CpuUsageAccumulatorTest {
    @Test
    fun `normalizes process by cores and main thread by one core`() {
        val info = CpuUsageAccumulator(4)
        info.add(CpuUsageSample(10.0, 2.0, 100.0))
        info.add(CpuUsageSample(12.0, 2.5, 101.0))
        val values = requireNotNull(info.values())
        assertThat(values.average).isEqualTo(50.0)
        assertThat(values.mainThreadAverage).isEqualTo(50.0)
        assertThat(values.max).isEqualTo(50.0)
    }

    @Test
    fun `averages by measured time not sample count and keeps interval peaks`() {
        val info = CpuUsageAccumulator(2)
        info.add(CpuUsageSample(0.0, 0.0, 0.0))
        info.add(CpuUsageSample(0.5, 0.25, 0.25))
        info.add(CpuUsageSample(0.5, 0.25, 1.0))
        val values = requireNotNull(info.values())
        assertThat(values.average).isEqualTo(25.0)
        assertThat(values.mainThreadAverage).isEqualTo(25.0)
        assertThat(values.max).isEqualTo(100.0)
        assertThat(values.mainThreadMax).isEqualTo(100.0)
    }

    @Test
    fun `does not measure gaps after background or failed reads`() {
        val info = CpuUsageAccumulator(2)
        info.add(CpuUsageSample(0.0, 0.0, 0.0))
        info.add(CpuUsageSample(0.5, 0.25, 0.5))
        info.add(null)
        info.add(CpuUsageSample(90.0, 80.0, 100.0))
        info.add(CpuUsageSample(90.5, 80.25, 100.5))
        assertThat(info.values()?.average).isEqualTo(50.0)
        assertThat(info.values()?.mainThreadAverage).isEqualTo(50.0)
    }

    @Test
    fun `distinguishes an unmeasured short view from an idle view`() {
        val info = CpuUsageAccumulator(8)
        assertThat(info.values()).isNull()
        info.add(CpuUsageSample(1.0, 1.0, 0.0))
        info.add(CpuUsageSample(1.0, 1.0, 0.89))
        assertThat(info.values()).isNull()
        info.add(CpuUsageSample(1.0, 1.0, 0.9))
        assertThat(info.values()).isEqualTo(CpuUsageValues(0.0, 0.0, 0.0, 0.0))
    }

    @Test
    fun `short trailing interval contributes to average and peak lower bound`() {
        val info = CpuUsageAccumulator(1)
        info.add(CpuUsageSample(0.0, 0.0, 0.0))
        info.add(CpuUsageSample(0.0, 0.0, 1.0))
        info.add(CpuUsageSample(0.1, 0.1, 1.1))
        val values = requireNotNull(info.values())
        assertThat(values.average).isCloseTo(100.0 / 11, offset(0.00001))
        assertThat(values.max).isEqualTo(values.average)
    }

    @Test
    fun `rejects decreasing counters and invalid intervals without negative or NaN values`() {
        val info = CpuUsageAccumulator(0)
        info.add(CpuUsageSample(10.0, 2.0, 1.0))
        info.add(CpuUsageSample(0.0, 0.0, 2.0))
        assertThat(info.values()).isNull()
        info.add(CpuUsageSample(2.0, 2.0, 3.0))
        assertThat(info.values()?.average).isEqualTo(100.0)
        assertThat(info.values()?.mainThreadAverage).isEqualTo(100.0)
    }
}
