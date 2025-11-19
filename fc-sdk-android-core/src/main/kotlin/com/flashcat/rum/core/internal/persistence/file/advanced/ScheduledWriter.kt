/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.core.internal.persistence.file.advanced

import androidx.annotation.WorkerThread
import com.flashcat.rum.api.InternalLogger
import com.flashcat.rum.core.internal.persistence.DataWriter
import com.flashcat.rum.core.internal.utils.executeSafe
import java.util.concurrent.ExecutorService

internal class ScheduledWriter<T : Any>(
    internal val delegateWriter: DataWriter<T>,
    internal val executorService: ExecutorService,
    private val internalLogger: InternalLogger
) : DataWriter<T> {

    // region DataWriter

    @WorkerThread
    override fun write(element: T) {
        executorService.executeSafe("Data writing", internalLogger) {
            delegateWriter.write(element)
        }
    }

    @WorkerThread
    override fun write(data: List<T>) {
        executorService.executeSafe("Data writing", internalLogger) {
            delegateWriter.write(data)
        }
    }

    // endregion
}
