/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.core.internal.persistence.file

import androidx.annotation.WorkerThread
import java.io.File

internal interface FileReader<T> {

    /**
     * Reads data from the given file.
     *  @param file the file to read from
     *  @return the data
     */
    @WorkerThread
    fun readData(file: File): T
}
