/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.vitals

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import java.io.File

internal class CpuUsageReaderTest {
    @TempDir
    lateinit var directory: File

    private fun stat(user: String, system: String): String =
        "123 (app (worker) name) S 1 2 3 4 5 6 7 8 9 10 $user $system 0 0"

    @Test
    fun `reads user plus system even when comm contains spaces and parentheses`() {
        assertThat(CpuUsageReader.cpuTicks(stat("100", "50"))).isEqualTo(150.0)
        assertThat(CpuUsageReader.cpuTicks("broken")).isNull()
        assertThat(CpuUsageReader.cpuTicks(stat("NaN", "50"))).isNull()
        assertThat(CpuUsageReader.cpuTicks(stat("-1", "50"))).isNull()
    }

    @Test
    fun `uses actual clock tick frequency and reads the main thread separately`() {
        val process = File(directory, "process").apply { writeText(stat("100", "50")) }
        val main = File(directory, "main").apply { writeText(stat("20", "5")) }
        val reader = CpuUsageReader(mock(), process, main, 250) { 42.0 }
        assertThat(reader.read()).isEqualTo(CpuUsageSample(0.6, 0.1, 42.0))
        assertThat(CpuUsageReader(mock(), process, main, -1) { 42.0 }.read()).isNull()
        main.writeText("unavailable")
        assertThat(reader.read()).isNull()
    }
}
