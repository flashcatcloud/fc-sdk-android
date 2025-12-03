/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.sessionreplay.material.internal

import android.graphics.drawable.Drawable
import cloud.flashcat.android.api.InternalLogger
import cloud.flashcat.android.sessionreplay.utils.DrawableToColorMapper
import com.google.android.material.shape.MaterialShapeDrawable

internal class MaterialDrawableToColorMapper : DrawableToColorMapper {

    override fun mapDrawableToColor(drawable: Drawable, internalLogger: InternalLogger): Int? {
        return when (drawable) {
            is MaterialShapeDrawable -> resolveMaterialShapeDrawable(drawable)
            else -> null
        }
    }

    private fun resolveMaterialShapeDrawable(
        shapeDrawable: MaterialShapeDrawable
    ): Int? {
        return shapeDrawable.fillColor?.defaultColor
    }
}
