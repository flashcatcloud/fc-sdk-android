/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.core.internal.metrics

import cloud.flashcat.tools.annotation.NoOpImplementation
import java.io.File

@NoOpImplementation
internal interface MetricsDispatcher {
    fun sendBatchDeletedMetric(batchFile: File, removalReason: RemovalReason, numPendingBatches: Int)

    fun sendBatchClosedMetric(batchFile: File, batchMetadata: BatchClosedMetadata)
}
