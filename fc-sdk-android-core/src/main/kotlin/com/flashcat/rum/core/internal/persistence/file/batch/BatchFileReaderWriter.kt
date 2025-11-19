/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.core.internal.persistence.file.batch

import com.flashcat.rum.api.InternalLogger
import com.flashcat.rum.api.storage.RawBatchEvent
import com.flashcat.rum.core.internal.persistence.file.FileWriter
import com.flashcat.rum.security.Encryption

internal interface BatchFileReaderWriter : FileWriter<RawBatchEvent>, BatchFileReader {

    companion object {
        /**
         * Creates either plain [PlainBatchFileReaderWriter] or [PlainBatchFileReaderWriter] wrapped in
         * [EncryptedBatchReaderWriter] if encryption is provided.
         */
        fun create(internalLogger: InternalLogger, encryption: Encryption?): BatchFileReaderWriter {
            val readerWriter = PlainBatchFileReaderWriter(internalLogger)
            return if (encryption == null) {
                readerWriter
            } else {
                EncryptedBatchReaderWriter(
                    encryption,
                    readerWriter,
                    internalLogger
                )
            }
        }
    }
}
