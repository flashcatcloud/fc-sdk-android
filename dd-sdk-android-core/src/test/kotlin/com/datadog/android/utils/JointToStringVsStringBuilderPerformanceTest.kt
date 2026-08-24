/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.utils

import com.datadog.android.internal.utils.appendIfNotEmpty
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt
import kotlin.system.measureNanoTime

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@Disabled(
    "Wall-clock micro-benchmark rather than a correctness test: it compares two string-building " +
        "approaches by measured time, so it flips on a shared CI runner and fails a release that is " +
        "otherwise sound. Run it locally when the performance claim needs revisiting."
)
internal class JointToStringVsStringBuilderPerformanceTest {

    @Test
    fun `M be faster than joinToString W buildString`(forge: Forge) {
        val itemsForJoin = forge.aList(ITEMS_TO_JOIN) { forge.aString() }
        val joinToStringExecutionTime = mutableListOf<Long>()
        val buildStringExecutionTime = mutableListOf<Long>()

        var jointToStringResult: String
        var builderResult: String

        repeat(REPETITION_COUNT) {
            joinToStringExecutionTime.add(
                measureNanoTime {
                    val jointToStringContainer = mutableListOf<String>()
                    for (item in itemsForJoin) {
                        jointToStringContainer.add(item)
                    }
                    jointToStringResult = jointToStringContainer.joinToString(separator = " ") { it }
                }
            )

            buildStringExecutionTime.add(
                measureNanoTime {
                    builderResult = buildString {
                        itemsForJoin.forEach { item -> appendIfNotEmpty(' ').append(item) }
                    }
                }
            )

            assertThat(builderResult).isEqualTo(jointToStringResult) // same result
        }

        val statisticsReport = (
            "buildString:\n" +
                " mean = ${buildStringExecutionTime.mean}\n" +
                " std = ${buildStringExecutionTime.std}\n" +
                " cv = ${"%.2f".format(buildStringExecutionTime.cv)}%\n" +
                " p50 = ${buildStringExecutionTime.percentile(50)}\n" +
                " p90 = ${buildStringExecutionTime.percentile(90)}\n" +
                " p95 = ${buildStringExecutionTime.percentile(95)}\n" +
                " p99 = ${buildStringExecutionTime.percentile(99)}\n" +
                "\n" +
                "joinToString:\n" +
                " mean = ${joinToStringExecutionTime.mean}\n" +
                " std = ${joinToStringExecutionTime.std}\n" +
                " cv = ${"%.2f".format(joinToStringExecutionTime.cv)}%\n" +
                " p50 = ${joinToStringExecutionTime.percentile(50)},\n" +
                " p90 = ${joinToStringExecutionTime.percentile(90)},\n" +
                " p95 = ${joinToStringExecutionTime.percentile(95)},\n" +
                " p99 = ${joinToStringExecutionTime.percentile(99)}\n"
            )

        assertThat(
            buildStringExecutionTime.percentile(90)
        ).withFailMessage(
            statisticsReport
        ).isLessThan(
            joinToStringExecutionTime.percentile(90)
        )
    }

    companion object {
        private const val ITEMS_TO_JOIN = 10_000
        private const val REPETITION_COUNT = 10_000

        private val List<Long>.mean
            get() = (sum().toDouble() / size)

        private val List<Long>.std: Double
            get() {
                val m = mean
                return sqrt(
                    sumOf { (it - m).pow(2.0) } / size
                )
            }

        private val List<Long>.cv: Double
            get() = std / mean * 100.0

        private fun List<Long>.percentile(k: Int): Long {
            val p = (k / 100.0) * (size + 1)
            return sorted()[round(p).toInt()]
        }
    }
}
