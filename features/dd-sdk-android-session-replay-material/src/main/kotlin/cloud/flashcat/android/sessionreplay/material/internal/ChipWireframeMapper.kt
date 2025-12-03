/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.sessionreplay.material.internal

import cloud.flashcat.android.api.InternalLogger
import cloud.flashcat.android.sessionreplay.ImagePrivacy
import cloud.flashcat.android.sessionreplay.model.MobileSegment
import cloud.flashcat.android.sessionreplay.recorder.MappingContext
import cloud.flashcat.android.sessionreplay.recorder.mapper.TextViewMapper
import cloud.flashcat.android.sessionreplay.utils.AsyncJobStatusCallback
import cloud.flashcat.android.sessionreplay.utils.ColorStringFormatter
import cloud.flashcat.android.sessionreplay.utils.DrawableToColorMapper
import cloud.flashcat.android.sessionreplay.utils.ViewBoundsResolver
import cloud.flashcat.android.sessionreplay.utils.ViewIdentifierResolver
import com.google.android.material.chip.Chip

internal class ChipWireframeMapper(
    viewIdentifierResolver: ViewIdentifierResolver,
    colorStringFormatter: ColorStringFormatter,
    viewBoundsResolver: ViewBoundsResolver,
    drawableToColorMapper: DrawableToColorMapper
) : TextViewMapper<Chip>(
    viewIdentifierResolver,
    colorStringFormatter,
    viewBoundsResolver,
    drawableToColorMapper
) {
    override fun map(
        view: Chip,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback,
        internalLogger: InternalLogger
    ): List<MobileSegment.Wireframe> {
        val wireframes = mutableListOf<MobileSegment.Wireframe>()

        val viewGlobalBounds = viewBoundsResolver.resolveViewGlobalBounds(
            view,
            mappingContext.systemInformation.screenDensity
        )
        val density = mappingContext.systemInformation.screenDensity
        val drawableBounds = view.chipDrawable.bounds
        val backgroundWireframe =
            mappingContext.imageWireframeHelper.createImageWireframeByDrawable(
                view = view,
                // Background drawable doesn't need to be masked.
                imagePrivacy = ImagePrivacy.MASK_NONE,
                currentWireframeIndex = 0,
                x = viewGlobalBounds.x + drawableBounds.left.toLong().densityNormalized(density),
                y = viewGlobalBounds.y + drawableBounds.top.toLong().densityNormalized(density),
                width = view.chipDrawable.intrinsicWidth,
                height = view.chipDrawable.intrinsicHeight,
                usePIIPlaceholder = false,
                drawable = view.chipDrawable,
                customResourceIdCacheKey = null,
                asyncJobStatusCallback = asyncJobStatusCallback
            )
        backgroundWireframe?.let {
            wireframes.add(it)
        }
        // Text wireframe
        wireframes.add(super.createTextWireframe(view, mappingContext, viewGlobalBounds))
        return wireframes.toList()
    }
}
