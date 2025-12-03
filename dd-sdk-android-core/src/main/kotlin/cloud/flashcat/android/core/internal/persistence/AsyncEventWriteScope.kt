/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.core.internal.persistence

import cloud.flashcat.android.api.InternalLogger
import cloud.flashcat.android.api.feature.EventWriteScope
import cloud.flashcat.android.api.storage.EventBatchWriter
import cloud.flashcat.android.core.internal.utils.executeSafe
import java.util.concurrent.Executor

internal class AsyncEventWriteScope(
    private val executor: Executor,
    private val writer: EventBatchWriter,
    private val featureWriteLock: Any,
    private val featureName: String,
    private val internalLogger: InternalLogger
) : EventWriteScope {
    override fun invoke(block: (EventBatchWriter) -> Unit) {
        executor.executeSafe("eventWriteScopeInvoke-$featureName", internalLogger) {
            // since writing may not be atomic: we can write batch data + batch metadata, there is a gap between
            // getting file for writing and write op, we sync file operation with a feature-wide lock
            synchronized(featureWriteLock) {
                block.invoke(writer)
            }
        }
    }
}
