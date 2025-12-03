/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.sessionreplay.forge

import cloud.flashcat.android.sessionreplay.internal.async.SnapshotRecordedDataQueueItem
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class SnapshotRecordedDataQueueItemForgeryFactory : ForgeryFactory<SnapshotRecordedDataQueueItem> {
    override fun getForgery(forge: Forge): SnapshotRecordedDataQueueItem {
        val item = SnapshotRecordedDataQueueItem(
            recordedQueuedItemContext = forge.getForgery(),
            systemInformation = forge.getForgery()
        )

        item.pendingJobs.set(forge.anInt())
        item.nodes = listOf(forge.getForgery())
        return item
    }
}
