/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal

import android.app.ApplicationExitInfo
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import com.datadog.android.api.storage.DataWriter
import com.google.gson.JsonObject

internal interface LateCrashReporter {

    fun handleNdkCrashEvent(event: Map<*, *>, rumWriter: DataWriter<Any>)

    /**
     * @param lastRumViewEventJson what the slot of the last view held at launch, if anything.
     * @param previousExitTimestamp when the run of the same process before the ANR ended, if known:
     * the crashed run started after it.
     * @param isLastExit whether the ANR ended the last run of its process.
     */
    @WorkerThread
    @RequiresApi(Build.VERSION_CODES.R)
    fun handleAnrCrash(
        anrExitInfo: ApplicationExitInfo,
        lastRumViewEventJson: JsonObject?,
        previousExitTimestamp: Long?,
        isLastExit: Boolean,
        rumWriter: DataWriter<Any>
    )
}
