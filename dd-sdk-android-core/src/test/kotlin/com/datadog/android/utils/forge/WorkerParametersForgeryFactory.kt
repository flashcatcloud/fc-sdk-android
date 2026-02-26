/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import android.content.Context
import androidx.work.Data
import androidx.work.ForegroundUpdater
import androidx.work.ListenableWorker
import androidx.work.ProgressUpdater
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.impl.utils.taskexecutor.TaskExecutor
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import org.mockito.kotlin.mock
import java.util.concurrent.Executor

class WorkerParametersForgeryFactory : ForgeryFactory<WorkerParameters> {

    // region ForgeryFactory

    override fun getForgery(forge: Forge): WorkerParameters {
        val sameThreadExecutor = object : Executor {
            override fun execute(command: Runnable) = command.run()
        }
        
        // Use Mockito to avoid direct implementation of internal interfaces
        val mockTaskExecutor = mock<TaskExecutor>()
        
        return WorkerParameters(
            forge.getForgery(),
            Data.EMPTY,
            forge.aList { anAlphabeticalString() },
            WorkerParameters.RuntimeExtras(),
            forge.aSmallInt(),
            sameThreadExecutor,
            mockTaskExecutor,
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker? = null
            },
            mock<ProgressUpdater>(),
            mock<ForegroundUpdater>()
        )
    }

    // endregion
}
