/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.sessionreplay.internal.prerequisite

import cloud.flashcat.android.api.InternalLogger
import cloud.flashcat.android.core.internal.persistence.file.listFilesSafe
import cloud.flashcat.android.internal.utils.allowThreadDiskReads
import java.io.File

internal class CPURequirementChecker(
    private val minCPUCores: Int,
    private val cpuDirFile: File = File(DIR_PATH),
    private val internalLogger: InternalLogger
) : SystemRequirementChecker {

    private var checkedValue: Int? = null

    override fun checkMinimumRequirement(): Boolean {
        if (minCPUCores == 0) {
            return true
        }
        val actualCPUCoreNumber = allowThreadDiskReads {
            readCPUCoreNumber()
        }
        return actualCPUCoreNumber >= minCPUCores
    }

    override fun name(): String = CPU_CHECK_NAME

    override fun checkedValue(): Any? = checkedValue

    private fun readCPUCoreNumber(): Int {
        val files = cpuDirFile.listFilesSafe(internalLogger) { _, name -> name.matches(REGEX_CPU_CORE_FILE) }
        val value = files?.size ?: fallbackReadCpuCoreNumber()
        checkedValue = value
        return value
    }

    private fun fallbackReadCpuCoreNumber(): Int {
        return Runtime.getRuntime().availableProcessors()
    }

    companion object {
        private const val DIR_PATH = "/sys/devices/system/cpu/"
        private val REGEX_CPU_CORE_FILE: Regex = Regex("cpu[0-9]+")

        /**
         * This name will be used in telemetry as the attribute key.
         */
        private const val CPU_CHECK_NAME = "cpu"
    }
}
