/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.core.internal.persistence.datastore

import com.flashcat.rum.api.storage.datastore.DataStoreHandler
import com.flashcat.rum.api.storage.datastore.DataStoreReadCallback
import com.flashcat.rum.api.storage.datastore.DataStoreWriteCallback
import com.flashcat.rum.core.internal.persistence.Deserializer
import com.flashcat.rum.core.persistence.Serializer

internal class NoOpDataStoreHandler : DataStoreHandler {
    override fun <T : Any> setValue(
        key: String,
        data: T,
        version: Int,
        callback: DataStoreWriteCallback?,
        serializer: Serializer<T>
    ) {
        // NoOp Implementation
    }

    override fun <T : Any> value(
        key: String,
        version: Int?,
        callback: DataStoreReadCallback<T>,
        deserializer: Deserializer<String, T>
    ) {
        // NoOp Implementation
    }

    override fun removeValue(
        key: String,
        callback: DataStoreWriteCallback?
    ) {
        // NoOp Implementation
    }

    override fun clearAllData() {
        // NoOp Implementation
    }
}
