/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.rum.coroutines

import cloud.flashcat.android.Flashcat
import cloud.flashcat.android.api.SdkCore
import cloud.flashcat.android.rum.GlobalRumMonitor
import cloud.flashcat.android.rum.RumErrorSource
import kotlinx.coroutines.flow.Flow
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
fun <T> Flow<T>.sendErrorToDatadog(sdkCore: SdkCore = Flashcat.getInstance()): Flow<T> {
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
