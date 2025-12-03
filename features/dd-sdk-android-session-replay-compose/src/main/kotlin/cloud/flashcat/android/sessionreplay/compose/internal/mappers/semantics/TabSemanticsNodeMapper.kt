/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.sessionreplay.compose.internal.mappers.semantics

import androidx.compose.ui.semantics.SemanticsNode
import cloud.flashcat.android.sessionreplay.compose.internal.data.SemanticsWireframe
import cloud.flashcat.android.sessionreplay.compose.internal.data.UiContext
import cloud.flashcat.android.sessionreplay.compose.internal.utils.SemanticsUtils
import cloud.flashcat.android.sessionreplay.model.MobileSegment
import cloud.flashcat.android.sessionreplay.utils.AsyncJobStatusCallback
import cloud.flashcat.android.sessionreplay.utils.ColorStringFormatter

internal class TabSemanticsNodeMapper(
    colorStringFormatter: ColorStringFormatter,
    semanticsUtils: SemanticsUtils = SemanticsUtils()
) : AbstractSemanticsNodeMapper(colorStringFormatter, semanticsUtils) {

    override fun map(
        semanticsNode: SemanticsNode,
        parentContext: UiContext,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): SemanticsWireframe {
        val globalBounds = resolveBounds(semanticsNode)
        val shapeStyle =
            MobileSegment.ShapeStyle(backgroundColor = parentContext.parentContentColor)
        val wireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = semanticsNode.id.toLong(),
            x = globalBounds.x,
            y = globalBounds.y,
            width = globalBounds.width,
            height = globalBounds.height,
            shapeStyle = shapeStyle
        )
        return SemanticsWireframe(
            wireframes = listOf(wireframe),
            uiContext = parentContext
        )
    }
}
