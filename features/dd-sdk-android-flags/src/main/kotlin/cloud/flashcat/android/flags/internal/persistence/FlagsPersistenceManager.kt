/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.flags.internal.persistence

import cloud.flashcat.android.api.InternalLogger
import cloud.flashcat.android.api.storage.datastore.DataStoreHandler
import cloud.flashcat.android.api.storage.datastore.DataStoreReadCallback
import cloud.flashcat.android.api.storage.datastore.DataStoreWriteCallback
import cloud.flashcat.android.core.persistence.datastore.DataStoreContent
import cloud.flashcat.android.flags.internal.model.FlagsStateEntry
import cloud.flashcat.android.flags.internal.model.PrecomputedFlag
import cloud.flashcat.android.flags.model.EvaluationContext

internal class FlagsPersistenceManager(
    private val dataStore: DataStoreHandler,
    instanceName: String,
    private val internalLogger: InternalLogger,
    onStateLoaded: (FlagsStateEntry?) -> Unit
) {
    private val serializer = FlagsStateSerializer(internalLogger)
    private val deserializer = FlagsStateDeserializer(internalLogger)
    private val flagsStateKey: String = "$FLAGS_STATE_KEY_PREFIX-$instanceName"

    init {
        loadFlagsState(onStateLoaded)
    }

    internal fun saveFlagsState(
        context: EvaluationContext,
        flags: Map<String, PrecomputedFlag>,
        callback: DataStoreWriteCallback? = null
    ) {
        val entry = FlagsStateEntry(
            evaluationContext = context,
            flags = flags,
            lastUpdateTimestamp = System.currentTimeMillis()
        )

        dataStore.setValue(
            key = flagsStateKey,
            data = entry,
            serializer = serializer,
            callback = callback
        )
    }

    private fun loadFlagsState(onStateLoaded: (FlagsStateEntry?) -> Unit) {
        dataStore.value(
            key = flagsStateKey,
            deserializer = deserializer,
            callback = object : DataStoreReadCallback<FlagsStateEntry> {
                override fun onSuccess(dataStoreContent: DataStoreContent<FlagsStateEntry>?) {
                    val loadedState = dataStoreContent?.data
                    onStateLoaded(loadedState)
                }

                override fun onFailure() {
                    internalLogger.log(
                        InternalLogger.Level.WARN,
                        InternalLogger.Target.MAINTAINER,
                        { "No persisted flags state found or failed to load" }
                    )
                    onStateLoaded(null)
                }
            }
        )
    }

    companion object {
        private const val FLAGS_STATE_KEY_PREFIX = "flags-state"
    }
}
