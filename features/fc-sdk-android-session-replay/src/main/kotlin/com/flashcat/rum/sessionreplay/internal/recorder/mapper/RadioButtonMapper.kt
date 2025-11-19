/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sessionreplay.internal.recorder.mapper

import android.widget.RadioButton
import androidx.annotation.UiThread
import com.flashcat.rum.api.InternalLogger
import com.flashcat.rum.sessionreplay.model.MobileSegment
import com.flashcat.rum.sessionreplay.recorder.mapper.TextViewMapper
import com.flashcat.rum.sessionreplay.utils.ColorStringFormatter
import com.flashcat.rum.sessionreplay.utils.DrawableToColorMapper
import com.flashcat.rum.sessionreplay.utils.ViewBoundsResolver
import com.flashcat.rum.sessionreplay.utils.ViewIdentifierResolver

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
