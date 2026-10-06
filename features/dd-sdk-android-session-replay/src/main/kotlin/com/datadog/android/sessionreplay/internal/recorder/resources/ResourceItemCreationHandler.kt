/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import androidx.annotation.VisibleForTesting
import com.datadog.android.sessionreplay.internal.async.DataQueueHandler
import java.util.Collections

internal class ResourceItemCreationHandler(
    private val recordedDataQueueHandler: DataQueueHandler
) {
    // resource IDs previously sent in this session -
    // optimization to avoid sending the same resource multiple times
    // atm this set is unbounded but expected to use relatively little space (~80kb per 1k items)
    @VisibleForTesting internal val resourceIdsSeen: MutableSet<String> =
        Collections.synchronizedSet(HashSet<String>())

    // FLASHCAT FORK - the resources dropped unsent from the store that held them for a replay kept
    // on error: they are queued again the next time they are shown, as if never seen.
    @VisibleForTesting internal val forgottenResourceIds: MutableSet<String> =
        Collections.synchronizedSet(HashSet<String>())

    internal fun queueItem(resourceId: String, resourceData: ByteArray) {
        if (!resourceIdsSeen.contains(resourceId)) {
            resourceIdsSeen.add(resourceId)
            forgottenResourceIds.remove(resourceId)

            recordedDataQueueHandler.addResourceItem(
                identifier = resourceId,
                resourceData = resourceData
            )
        }
    }

    /** FLASHCAT FORK - whether a resource once queued was dropped unsent since. */
    internal fun isForgotten(resourceId: String): Boolean = forgottenResourceIds.contains(resourceId)

    /** FLASHCAT FORK - see [forgottenResourceIds]. */
    internal fun forget(resourceIds: Collection<String>) {
        resourceIdsSeen.removeAll(resourceIds.toSet())
        forgottenResourceIds.addAll(resourceIds)
    }
}
