/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.sessionreplay.internal.recorder.mapper

import android.widget.RadioButton
import androidx.annotation.UiThread
import cloud.flashcat.android.api.InternalLogger
import cloud.flashcat.android.sessionreplay.model.MobileSegment
import cloud.flashcat.android.sessionreplay.recorder.mapper.TextViewMapper
import cloud.flashcat.android.sessionreplay.utils.ColorStringFormatter
import cloud.flashcat.android.sessionreplay.utils.DrawableToColorMapper
import cloud.flashcat.android.sessionreplay.utils.ViewBoundsResolver
import cloud.flashcat.android.sessionreplay.utils.ViewIdentifierResolver

internal open class RadioButtonMapper(
    textWireframeMapper: TextViewMapper<RadioButton>,
    viewIdentifierResolver: ViewIdentifierResolver,
    colorStringFormatter: ColorStringFormatter,
    viewBoundsResolver: ViewBoundsResolver,
    drawableToColorMapper: DrawableToColorMapper,
    internalLogger: InternalLogger
) : CheckableCompoundButtonMapper<RadioButton>(
    textWireframeMapper,
    viewIdentifierResolver,
    colorStringFormatter,
    viewBoundsResolver,
    drawableToColorMapper,
    internalLogger
) {

    // region CheckableTextViewMapper

    @UiThread
    override fun resolveNotCheckedShapeStyle(view: RadioButton, checkBoxColor: String): MobileSegment.ShapeStyle? {
        return MobileSegment.ShapeStyle(
            backgroundColor = null,
            view.alpha,
            cornerRadius = CORNER_RADIUS
        )
    }

    // endregion

    companion object {
        internal const val CORNER_RADIUS = 10
    }
}
