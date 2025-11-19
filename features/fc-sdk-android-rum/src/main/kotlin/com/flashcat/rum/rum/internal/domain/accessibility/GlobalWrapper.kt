/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.rum.internal.domain.accessibility

import android.content.Context
import android.provider.Settings
import android.provider.Settings.SettingNotFoundException
import com.flashcat.rum.api.InternalLogger

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
