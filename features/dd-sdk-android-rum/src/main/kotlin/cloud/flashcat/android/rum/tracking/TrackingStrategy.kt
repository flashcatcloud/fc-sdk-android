/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.rum.tracking

import android.content.Context
import cloud.flashcat.android.api.SdkCore
import cloud.flashcat.tools.annotation.NoOpImplementation

/**
 * The TrackingStrategy interface.
 */
@NoOpImplementation
interface TrackingStrategy {

    /**
     * This method will register the tracking strategy to the current Context and SDK instance.
     * @param sdkCore as [SdkCore]
     * @param context as [Context]
     */
    fun register(sdkCore: SdkCore, context: Context)

    /**
     * This method will unregister the tracking strategy from the current Context.
     * @param context as [Context]
     */
    fun unregister(context: Context?)
}
