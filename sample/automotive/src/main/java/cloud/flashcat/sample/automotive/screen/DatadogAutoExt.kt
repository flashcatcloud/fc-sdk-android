/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.sample.automotive.screen

import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.OnClickListener
import cloud.flashcat.android.Flashcat
import cloud.flashcat.android.api.SdkCore
import cloud.flashcat.android.rum.GlobalRumMonitor
import cloud.flashcat.android.rum.RumActionType

internal fun Screen.monitorGetTemplate(
    sdkCore: SdkCore = Flashcat.getInstance()
) {
    GlobalRumMonitor.get(sdkCore).startView(
        key = javaClass.name,
        name = javaClass.simpleName
    )
}

internal fun Action.Builder.setMonitoredClickListener(
    sdkCore: SdkCore = Flashcat.getInstance(),
    listener: OnClickListener
): Action.Builder {
    val builtAction = build()
    return setOnClickListener {
        GlobalRumMonitor.get(sdkCore).addAction(
            type = RumActionType.TAP,
            name = builtAction.title?.toString() ?: builtAction.icon.toString()
        )
        listener.onClick()
    }
}
