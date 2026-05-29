/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.utils

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.UploadWorker
import java.lang.reflect.Method
import java.util.concurrent.TimeUnit

internal const val CANCEL_ERROR_MESSAGE = "Error cancelling the UploadWorker"
internal const val SETUP_ERROR_MESSAGE = "Error while trying to setup the UploadWorker"
internal const val UPLOAD_WORKER_WAS_SCHEDULED = "UploadWorker was scheduled."
internal const val UPLOAD_WORKER_NAME = "DatadogUploadWorker"
internal const val TAG_DATADOG_UPLOAD = "DatadogBackgroundUpload"

internal const val DELAY_MS: Long = 5000

internal fun cancelUploadWorker(
    context: Context,
    instanceName: String,
    internalLogger: InternalLogger
) {
    try {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelAllWorkByTag("$TAG_DATADOG_UPLOAD/$instanceName")
    } catch (e: IllegalStateException) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            { CANCEL_ERROR_MESSAGE },
            e
        )
    }
}

@Suppress("TooGenericExceptionCaught")
internal fun triggerUploadWorker(
    context: Context,
    instanceName: String,
    internalLogger: InternalLogger
) {
    try {
        val workManager = WorkManager.getInstance(context)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_ROAMING)
            .build()
        val uploadWorkRequest = OneTimeWorkRequest.Builder(UploadWorker::class.java)
            .setConstraints(constraints)
            .addTag("$TAG_DATADOG_UPLOAD/$instanceName")
            .setInitialDelay(DELAY_MS, TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putString(UploadWorker.DATADOG_INSTANCE_NAME, instanceName).build())
            .build()
        workManager.enqueueUniqueWork(
            UPLOAD_WORKER_NAME,
            ExistingWorkPolicy.REPLACE,
            uploadWorkRequest
        )
        internalLogger.log(
            InternalLogger.Level.INFO,
            InternalLogger.Target.MAINTAINER,
            { UPLOAD_WORKER_WAS_SCHEDULED }
        )
    } catch (e: Exception) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            { SETUP_ERROR_MESSAGE },
            e
        )
    }
}

private const val TAG = "FC_SDK_WorkManager"

private val isClassAvailable: Boolean by lazy {
    try {
        Class.forName("androidx.work.WorkManager")
        true
    } catch (e: Throwable) {
        false
    }
}

private val officialIsInitializedMethod: Method? by lazy {
    if (!isClassAvailable) return@lazy null
    try {
        WorkManager::class.java.getMethod("isInitialized")
    } catch (e: Throwable) {
        null
    }
}

@Volatile
private var isInitializedCache: Boolean? = null

/**
 * Checks whether WorkManager has been initialized.
 * Compatible with WorkManager 2.8.0+ (reflective call to the official method) and older versions
 * (try-catch around getInstance). Safely handles the class being absent due to compileOnly.
 */
internal fun isInitialized(context: Context): Boolean {
    isInitializedCache?.let { return it }

    if (!isClassAvailable) {
        return false.also { isInitializedCache = it }
    }

    officialIsInitializedMethod?.let { method ->
        try {
            val result = method.invoke(null) as Boolean
            if (result) {
                isInitializedCache = true
            }
            return result
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to invoke isInitialized via reflection", e)
        }
    }

    return try {
        WorkManager.getInstance(context)
        true.also { isInitializedCache = it }
    } catch (e: IllegalStateException) {
        false
    } catch (e: Throwable) {
        false
    }
}
