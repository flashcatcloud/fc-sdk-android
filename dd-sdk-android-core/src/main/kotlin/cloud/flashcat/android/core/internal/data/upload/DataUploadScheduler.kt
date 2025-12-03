/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.core.internal.data.upload

import cloud.flashcat.android.api.InternalLogger
import cloud.flashcat.android.core.configuration.UploadSchedulerStrategy
import cloud.flashcat.android.core.internal.ContextProvider
import cloud.flashcat.android.core.internal.net.info.NetworkInfoProvider
import cloud.flashcat.android.core.internal.persistence.Storage
import cloud.flashcat.android.core.internal.system.SystemInfoProvider
import cloud.flashcat.android.core.internal.utils.executeSafe
import java.util.concurrent.ScheduledThreadPoolExecutor

internal class DataUploadScheduler(
    private val featureName: String,
    storage: Storage,
    dataUploader: DataUploader,
    contextProvider: ContextProvider,
    networkInfoProvider: NetworkInfoProvider,
    systemInfoProvider: SystemInfoProvider,
    uploadSchedulerStrategy: UploadSchedulerStrategy,
    maxBatchesPerJob: Int,
    private val scheduledThreadPoolExecutor: ScheduledThreadPoolExecutor,
    private val internalLogger: InternalLogger
) : UploadScheduler {

    internal val runnable = DataUploadRunnable(
        featureName = featureName,
        threadPoolExecutor = scheduledThreadPoolExecutor,
        storage = storage,
        dataUploader = dataUploader,
        contextProvider = contextProvider,
        networkInfoProvider = networkInfoProvider,
        systemInfoProvider = systemInfoProvider,
        uploadSchedulerStrategy = uploadSchedulerStrategy,
        maxBatchesPerJob = maxBatchesPerJob,
        internalLogger = internalLogger
    )

    override fun startScheduling() {
        scheduledThreadPoolExecutor.executeSafe(
            "$featureName: data upload",
            internalLogger,
            runnable
        )
    }

    override fun stopScheduling() {
        scheduledThreadPoolExecutor.remove(runnable)
    }
}
