/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sessionreplay.internal.async

import com.flashcat.rum.sessionreplay.internal.processor.RecordedQueuedItemContext
import com.flashcat.rum.sessionreplay.internal.recorder.Node
import com.flashcat.rum.sessionreplay.recorder.SystemInformation
import java.util.concurrent.atomic.AtomicInteger

internal class SnapshotRecordedDataQueueItem(
    recordedQueuedItemContext: RecordedQueuedItemContext,
    internal val systemInformation: SystemInformation
) : RecordedDataQueueItem(recordedQueuedItemContext) {
    @Volatile internal var nodes = emptyList<Node>()

    @Volatile internal var isFinishedTraversal = false
    internal val pendingJobs = AtomicInteger(0)

    override fun isValid(): Boolean {
        if (!isFinishedTraversal) {
            // item is always valid unless traversal has finished
            return true
        }

        return nodes.isNotEmpty()
    }

    override fun isReady(): Boolean {
        return isFinishedTraversal && pendingJobs.get() == 0
    }

    internal fun incrementPendingJobs() = pendingJobs.incrementAndGet()
    internal fun decrementPendingJobs() = pendingJobs.decrementAndGet()
}
