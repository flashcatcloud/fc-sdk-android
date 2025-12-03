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
import cloud.flashcat.android.sessionreplay.utils.AsyncJobStatusCallback
import cloud.flashcat.android.sessionreplay.utils.ColorStringFormatter

internal class ImageSemanticsNodeMapper(
    colorStringFormatter: ColorStringFormatter,
    private val semanticsUtils: SemanticsUtils
) : AbstractSemanticsNodeMapper(colorStringFormatter) {

    override fun map(
        semanticsNode: SemanticsNode,
        parentContext: UiContext,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): SemanticsWireframe {
        val bounds = resolveBounds(semanticsNode)
        val bitmapInfo = semanticsUtils.resolveSemanticsPainter(semanticsNode)
        val containerFrames = resolveModifierWireframes(semanticsNode).toMutableList()
        val imagePrivacy =
            semanticsUtils.getImagePrivacyOverride(semanticsNode) ?: parentContext.imagePrivacy
        val imageWireframe = if (bitmapInfo != null) {
            parentContext.imageWireframeHelper.createImageWireframeByBitmap(
                id = semanticsNode.id.toLong(),
                globalBounds = bounds,
                bitmap = bitmapInfo.bitmap,
                density = parentContext.density,
                isContextualImage = bitmapInfo.isContextualImage,
                imagePrivacy = imagePrivacy,
                asyncJobStatusCallback = asyncJobStatusCallback,
                clipping = null,
                shapeStyle = null,
                border = null
            )
        } else {
            null
        }
        imageWireframe?.let {
            containerFrames.add(it)
        }
        return SemanticsWireframe(
            wireframes = containerFrames,
            uiContext = null
        )
    }
}
