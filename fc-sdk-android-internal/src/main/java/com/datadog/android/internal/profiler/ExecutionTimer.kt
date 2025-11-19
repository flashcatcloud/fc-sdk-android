/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.internal.profiler

/**
 * Interface for execution timers to measure the duration of actions. This should only be used by internal
 * benchmarking.
 */
interface ExecutionTimer {

    /**
     * Wraps the action to measure the time it took to execute.
     * @param T The type of the result.
     * @param action The action to measure.
     */
    fun <T>measure(action: () -> T): T
}
