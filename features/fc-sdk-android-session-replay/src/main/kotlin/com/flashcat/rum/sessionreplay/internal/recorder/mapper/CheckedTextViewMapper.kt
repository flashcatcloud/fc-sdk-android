/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sessionreplay.internal.recorder.mapper

import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableContainer
import android.widget.CheckedTextView
import androidx.annotation.UiThread
import com.flashcat.rum.internal.utils.densityNormalized
import com.flashcat.rum.sessionreplay.recorder.mapper.TextViewMapper
import com.flashcat.rum.sessionreplay.utils.ColorStringFormatter
import com.flashcat.rum.sessionreplay.utils.DrawableToColorMapper
import com.flashcat.rum.sessionreplay.utils.GlobalBounds
import com.flashcat.rum.sessionreplay.utils.OPAQUE_ALPHA_VALUE
import com.flashcat.rum.sessionreplay.utils.ViewBoundsResolver
import com.flashcat.rum.sessionreplay.utils.ViewIdentifierResolver

internal open class CheckedTextViewMapper(
    textWireframeMapper: TextViewMapper<CheckedTextView>,
    viewIdentifierResolver: ViewIdentifierResolver,
    colorStringFormatter: ColorStringFormatter,
    viewBoundsResolver: ViewBoundsResolver,
    drawableToColorMapper: DrawableToColorMapper
) : CheckableTextViewMapper<CheckedTextView>(
    textWireframeMapper,
    viewIdentifierResolver,
    colorStringFormatter,
    viewBoundsResolver,
    drawableToColorMapper
) {

    // region CheckableTextViewMapper

    @UiThread
    override fun resolveCheckableColor(view: CheckedTextView): String {
        val color = view.checkMarkTintList?.defaultColor ?: view.currentTextColor
        return colorStringFormatter.formatColorAndAlphaAsHexString(color, OPAQUE_ALPHA_VALUE)
    }

    @UiThread
    override fun resolveCheckableBounds(view: CheckedTextView, pixelsDensity: Float): GlobalBounds {
        val viewGlobalBounds = viewBoundsResolver.resolveViewGlobalBounds(view, pixelsDensity)
        val textViewPaddingRight =
            view.totalPaddingRight.toLong().densityNormalized(pixelsDensity)
        var checkBoxHeight = 0L
        val checkMarkDrawable = view.checkMarkDrawable
        if (checkMarkDrawable != null && checkMarkDrawable.intrinsicHeight > 0) {
            val height = checkMarkDrawable.intrinsicHeight -
                view.totalPaddingTop -
                view.totalPaddingBottom
            checkBoxHeight = height.toLong().densityNormalized(pixelsDensity)
        }

        return GlobalBounds(
            x = viewGlobalBounds.x + viewGlobalBounds.width - textViewPaddingRight,
            y = viewGlobalBounds.y,
            width = checkBoxHeight,
            height = checkBoxHeight

        )
    }

    override fun getCheckableDrawable(view: CheckedTextView): Drawable? {
        // drawable from [CheckedTextView] can not be retrieved according to the state,
        // so here two hardcoded indexes are used to retrieve "checked" and "not checked" drawables.
        val checkableDrawableIndex = if (view.isChecked) {
            CHECK_BOX_CHECKED_DRAWABLE_INDEX
        } else {
            CHECK_BOX_NOT_CHECKED_DRAWABLE_INDEX
        }

        return (view.checkMarkDrawable?.constantState as? DrawableContainer.DrawableContainerState)?.getChild(
            checkableDrawableIndex
        )
    }

    override fun cloneCheckableDrawable(view: CheckedTextView, drawable: Drawable): Drawable? {
        return drawable.constantState?.newDrawable(view.resources)?.apply {
            // Set state to make the drawable have correct tint according to the state.
            setState(view.drawableState)
            // Set tint list to drawable if the button has declared `checkMarkTint` attribute.
            view.checkMarkTintList?.let {
                setTintList(view.checkMarkTintList)
            }
        }
    }
    // endregion
}
