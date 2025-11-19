/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.rum.coroutines

import com.flashcat.rum.Flashcat
import com.flashcat.rum.api.SdkCore
import com.flashcat.rum.rum.GlobalRumMonitor
import com.flashcat.rum.rum.RumErrorSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

internal const val ERROR_FLOW: String = "Coroutine Flow error"

/**
 *  Returns a [Flow] that will send a RUM Error event if this [Flow] emits an error.
 *  Note that the error will also be emitted by the returned [Flow].
 *
 *  @param T the type of data in the [Flow].
 *  @param sdkCore SDK instance to use for reporting. If not provided, default instance will
 *  be used.
 *
 *  @return the new [Flow] instance.
 */
@Suppress("TooGenericExceptionCaught")
fun <T> Flow<T>.sendErrorToDatadog(sdkCore: SdkCore = Datadog.getInstance()): Flow<T> {
    return flow {
        try {
            collect { value -> emit(value) }
        } catch (e: Throwable) {
            GlobalRumMonitor.get(sdkCore)
                .addError(
                    ERROR_FLOW,
                    RumErrorSource.SOURCE,
                    e
                )
            throw e
        }
    }
}
