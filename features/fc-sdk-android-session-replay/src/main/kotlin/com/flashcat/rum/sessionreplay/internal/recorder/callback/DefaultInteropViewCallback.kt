/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sessionreplay.internal.recorder.callback

import android.view.View
import androidx.annotation.UiThread
import com.flashcat.rum.sessionreplay.internal.async.RecordedDataQueueRefs
import com.flashcat.rum.sessionreplay.internal.recorder.TreeViewTraversal
import com.flashcat.rum.sessionreplay.model.MobileSegment
import com.flashcat.rum.sessionreplay.recorder.InteropViewCallback
import com.flashcat.rum.sessionreplay.recorder.MappingContext

internal class DefaultInteropViewCallback(
    private val treeViewTraversal: TreeViewTraversal,
    private val recordedDataQueueRefs: RecordedDataQueueRefs
) : InteropViewCallback {

    @UiThread
    override fun map(view: View, mappingContext: MappingContext): List<MobileSegment.Wireframe> {
        return treeViewTraversal.traverse(
            view,
            mappingContext,
            recordedDataQueueRefs
        ).mappedWireframes
    }
}
