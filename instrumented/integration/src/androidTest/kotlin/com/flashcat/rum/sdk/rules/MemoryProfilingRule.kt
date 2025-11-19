/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sdk.rules

internal class MemoryProfilingRule :
    AbstractProfilingRule(name = "Memory usage in Kb") {

    // region AbstractProfilingRule

    override fun measure(): Double {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()).toDouble() / 1024.0
    }

    // endregion
}
