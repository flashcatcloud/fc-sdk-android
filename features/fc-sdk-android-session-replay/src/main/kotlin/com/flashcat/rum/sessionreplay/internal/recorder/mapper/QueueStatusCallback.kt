/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sessionreplay.internal.recorder.mapper

import com.flashcat.rum.sessionreplay.internal.async.RecordedDataQueueRefs
import com.flashcat.rum.sessionreplay.utils.AsyncJobStatusCallback

internal class QueueStatusCallback(
    private val recordedDataQueueRefs: RecordedDataQueueRefs
) : AsyncJobStatusCallback {

    override fun jobStarted() {
        recordedDataQueueRefs.incrementPendingJobs()
    }

    override fun jobFinished() {
        recordedDataQueueRefs.decrementPendingJobs()
        recordedDataQueueRefs.tryToConsumeItem()
    }
}
