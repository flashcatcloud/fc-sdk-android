/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.sample.automotive.screen

import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.OnClickListener
import com.flashcat.rum.Flashcat
import com.flashcat.rum.api.SdkCore
import com.flashcat.rum.rum.GlobalRumMonitor
import com.flashcat.rum.rum.RumActionType

internal fun Screen.monitorGetTemplate(
    sdkCore: SdkCore = Datadog.getInstance()
) {
    GlobalRumMonitor.get(sdkCore).startView(
        key = javaClass.name,
        name = javaClass.simpleName
    )
}

internal fun Action.Builder.setMonitoredClickListener(
    sdkCore: SdkCore = Datadog.getInstance(),
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
