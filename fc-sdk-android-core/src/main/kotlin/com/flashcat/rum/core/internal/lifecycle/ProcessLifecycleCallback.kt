/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.core.internal.lifecycle

import android.content.Context
import androidx.work.WorkManager
import com.flashcat.rum.api.InternalLogger
import com.flashcat.rum.core.internal.utils.cancelUploadWorker
import com.flashcat.rum.core.internal.utils.triggerUploadWorker
import java.lang.ref.Reference
import java.lang.ref.WeakReference

internal class ProcessLifecycleCallback(
    appContext: Context,
    internal val instanceName: String,
    private val internalLogger: InternalLogger
) :
    ProcessLifecycleMonitor.Callback {

    internal val contextWeakRef: Reference<Context> = WeakReference(appContext)

    override fun onStarted() {
        contextWeakRef.get()?.let {
            if (WorkManager.isInitialized()) {
                cancelUploadWorker(it, instanceName, internalLogger)
            }
        }
    }

    override fun onResumed() {
        // NO - OP
    }

    override fun onStopped() {
        contextWeakRef.get()?.let {
            if (WorkManager.isInitialized()) {
                triggerUploadWorker(it, instanceName, internalLogger)
            }
        }
    }

    override fun onPaused() {
        // NO - OP
    }
}
