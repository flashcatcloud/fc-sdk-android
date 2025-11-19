/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sessionreplay.internal.processor

import com.flashcat.rum.sessionreplay.internal.async.ResourceRecordedDataQueueItem
import com.flashcat.rum.sessionreplay.internal.async.SnapshotRecordedDataQueueItem
import com.flashcat.rum.sessionreplay.internal.async.TouchEventRecordedDataQueueItem

internal interface Processor {

    fun processResources(item: ResourceRecordedDataQueueItem)

    fun processScreenSnapshots(item: SnapshotRecordedDataQueueItem)

    fun processTouchEventsRecords(item: TouchEventRecordedDataQueueItem)
}
