/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.rum.internal

import android.app.ApplicationExitInfo
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import com.flashcat.rum.api.storage.DataWriter
import com.google.gson.JsonObject

internal interface LateCrashReporter {

    fun handleNdkCrashEvent(event: Map<*, *>, rumWriter: DataWriter<Any>)

    @WorkerThread
    @RequiresApi(Build.VERSION_CODES.R)
    fun handleAnrCrash(
        anrExitInfo: ApplicationExitInfo,
        lastRumViewEventJson: JsonObject,
        rumWriter: DataWriter<Any>
    )
}
