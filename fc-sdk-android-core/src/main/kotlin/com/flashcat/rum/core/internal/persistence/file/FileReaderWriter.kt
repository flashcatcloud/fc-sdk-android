/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.core.internal.persistence.file

import com.flashcat.rum.api.InternalLogger
import com.flashcat.rum.security.Encryption

internal interface FileReaderWriter : FileWriter<ByteArray>, FileReader<ByteArray> {
    companion object {

        /**
         * Creates either plain [PlainFileReaderWriter] or [PlainFileReaderWriter] wrapped in
         * [EncryptedFileReaderWriter] if encryption is provided.
         */
        fun create(internalLogger: InternalLogger, encryption: Encryption?): FileReaderWriter {
            val readerWriter = PlainFileReaderWriter(internalLogger)
            return if (encryption == null) {
                readerWriter
            } else {
                EncryptedFileReaderWriter(
                    encryption,
                    readerWriter,
                    internalLogger
                )
            }
        }
    }
}
