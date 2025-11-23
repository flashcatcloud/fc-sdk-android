/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package com.datadog.android.rum.internal.domain.accessibility

import android.content.Context
import android.provider.Settings
import android.provider.Settings.SettingNotFoundException
import com.flashcat.android.api.InternalLogger

internal class GlobalWrapper {
    @Suppress("UnsafeThirdPartyFunctionCall")
    internal fun getFloat(
        internalLogger: InternalLogger,
        applicationContext: Context,
        key: String
    ): Float? {
        return try {
            Settings.Global.getFloat(
                applicationContext.contentResolver,
                key
            )
        } catch (e: SettingNotFoundException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER),
                { "Setting not found $key" },
                e
            )
            null
        }
    }
}
