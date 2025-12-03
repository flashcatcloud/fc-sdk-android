/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.sessionreplay.internal.processor

import cloud.flashcat.android.sessionreplay.internal.async.ResourceRecordedDataQueueItem
import cloud.flashcat.android.sessionreplay.internal.async.SnapshotRecordedDataQueueItem
import cloud.flashcat.android.sessionreplay.internal.async.TouchEventRecordedDataQueueItem

internal interface Processor {

    fun processResources(item: ResourceRecordedDataQueueItem)

    fun processScreenSnapshots(item: SnapshotRecordedDataQueueItem)

    fun processTouchEventsRecords(item: TouchEventRecordedDataQueueItem)
}
