/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.rum.internal.utils

import cloud.flashcat.android.api.SdkCore
import cloud.flashcat.android.rum.GlobalRumMonitor
import cloud.flashcat.android.rum.RumErrorSource

internal const val CLOSABLE_ERROR_MESSAGE = "Error while using the closeable"

internal fun handleClosableError(throwable: Throwable, sdkCore: SdkCore) {
    GlobalRumMonitor.get(sdkCore).addError(CLOSABLE_ERROR_MESSAGE, RumErrorSource.SOURCE, throwable)
}
