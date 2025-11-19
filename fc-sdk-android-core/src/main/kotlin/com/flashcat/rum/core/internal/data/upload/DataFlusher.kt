/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.core.internal.data.upload

import androidx.annotation.WorkerThread
import com.flashcat.rum.api.InternalLogger
import com.flashcat.rum.core.internal.ContextProvider
import com.flashcat.rum.core.internal.persistence.file.FileMover
import com.flashcat.rum.core.internal.persistence.file.FileOrchestrator
import com.flashcat.rum.core.internal.persistence.file.FileReader
import com.flashcat.rum.core.internal.persistence.file.batch.BatchFileReader
import com.flashcat.rum.core.internal.persistence.file.existsSafe

internal class DataFlusher(
    internal val contextProvider: ContextProvider,
    internal val fileOrchestrator: FileOrchestrator,
    internal val fileReader: BatchFileReader,
    internal val metadataFileReader: FileReader<ByteArray>,
    internal val fileMover: FileMover,
    private val internalLogger: InternalLogger
) : Flusher {

    @WorkerThread
    override fun flush(uploader: DataUploader) {
        val context = contextProvider.getContext(withFeatureContexts = emptySet())

        val toUploadFiles = fileOrchestrator.getFlushableFiles()
        toUploadFiles.forEach {
            val batch = fileReader.readData(it)
            val metaFile = fileOrchestrator.getMetadataFile(it)
            val meta = if (metaFile != null && metaFile.existsSafe(internalLogger)) {
                metadataFileReader.readData(metaFile)
            } else {
                null
            }
            uploader.upload(context, batch, meta)
            fileMover.delete(it)
            if (metaFile?.existsSafe(internalLogger) == true) {
                fileMover.delete(metaFile)
            }
        }
    }
}
