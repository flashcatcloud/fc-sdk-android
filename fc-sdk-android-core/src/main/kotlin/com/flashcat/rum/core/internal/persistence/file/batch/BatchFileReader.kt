/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.core.internal.persistence.file.batch

import androidx.annotation.WorkerThread
import com.flashcat.rum.api.storage.RawBatchEvent
import java.io.File

internal interface BatchFileReader {

    /**
     * Reads data from the given file.
     *  @param file the file to read from
     *  @return the list of events as [RawBatchEvent] data stored in a file.
     */
    @WorkerThread
    fun readData(
        file: File
    ): List<RawBatchEvent>
}
