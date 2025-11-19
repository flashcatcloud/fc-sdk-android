/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sessionreplay.compose.internal.mappers.semantics

import androidx.compose.ui.semantics.SemanticsNode
import com.flashcat.rum.sessionreplay.compose.internal.data.SemanticsWireframe
import com.flashcat.rum.sessionreplay.compose.internal.data.UiContext
import com.flashcat.rum.sessionreplay.utils.AsyncJobStatusCallback

internal interface SemanticsNodeMapper {

    fun map(
        semanticsNode: SemanticsNode,
        parentContext: UiContext,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): SemanticsWireframe?
}
