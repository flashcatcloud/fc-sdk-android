/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.  * Modified 2025 by FlashCat, Inc.
 */

package com.flashcat.android.core.internal.persistence.file.batch

import androidx.annotation.WorkerThread
import com.flashcat.android.api.storage.RawBatchEvent
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
