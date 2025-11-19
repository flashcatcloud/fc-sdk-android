/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sessionreplay.material.internal

import android.widget.TextView
import androidx.annotation.UiThread
import com.flashcat.rum.api.InternalLogger
import com.flashcat.rum.sessionreplay.model.MobileSegment
import com.flashcat.rum.sessionreplay.recorder.MappingContext
import com.flashcat.rum.sessionreplay.recorder.SystemInformation
import com.flashcat.rum.sessionreplay.recorder.mapper.TextViewMapper
import com.flashcat.rum.sessionreplay.recorder.mapper.WireframeMapper
import com.flashcat.rum.sessionreplay.utils.AsyncJobStatusCallback
import com.flashcat.rum.sessionreplay.utils.ColorStringFormatter
import com.flashcat.rum.sessionreplay.utils.DrawableToColorMapper
import com.flashcat.rum.sessionreplay.utils.ViewBoundsResolver
import com.flashcat.rum.sessionreplay.utils.ViewIdentifierResolver
import com.google.android.material.tabs.TabLayout.TabView

internal open class TabWireframeMapper(
    private val viewIdentifierResolver: ViewIdentifierResolver,
    private val viewBoundsResolver: ViewBoundsResolver,
    internal val textViewMapper: WireframeMapper<TextView>
) : WireframeMapper<TabView> {

    constructor(
        viewIdentifierResolver: ViewIdentifierResolver,
        colorStringFormatter: ColorStringFormatter,
        viewBoundsResolver: ViewBoundsResolver,
        drawableToColorMapper: DrawableToColorMapper
    ) : this(
        viewIdentifierResolver,
        viewBoundsResolver,
        TextViewMapper(
            viewIdentifierResolver,
            colorStringFormatter,
            viewBoundsResolver,
            drawableToColorMapper
        )
    )

    @UiThread
    override fun map(
        view: TabView,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback,
        internalLogger: InternalLogger
    ): List<MobileSegment.Wireframe> {
        val labelWireframes = findAndResolveLabelWireframes(
            view,
            mappingContext,
            asyncJobStatusCallback,
            internalLogger
        )
        if (view.isSelected) {
            val selectedTabIndicatorWireframe = resolveTabIndicatorWireframe(
                view,
                mappingContext.systemInformation,
                labelWireframes.firstOrNull()
            )
            if (selectedTabIndicatorWireframe != null) {
                return labelWireframes + selectedTabIndicatorWireframe
            }
        }
        return labelWireframes
    }

    protected open fun resolveTabIndicatorWireframe(
        view: TabView,
        systemInformation: SystemInformation,
        wireframe: MobileSegment.Wireframe?
    ): MobileSegment.Wireframe? {
        val selectorId = viewIdentifierResolver.resolveChildUniqueIdentifier(
            view,
            SELECTED_TAB_INDICATOR_KEY_NAME
        ) ?: return null
        val screenDensity = systemInformation.screenDensity
        val viewBounds = viewBoundsResolver.resolveViewGlobalBounds(view, screenDensity)
        val selectionIndicatorHeight = SELECTED_TAB_INDICATOR_HEIGHT_IN_PX
            .densityNormalized(screenDensity)
        val paddingStart = view.paddingStart.toLong().densityNormalized(screenDensity)
        val paddingEnd = view.paddingEnd.toLong().densityNormalized(screenDensity)
        val selectionIndicatorXPos = viewBounds.x + paddingStart
        val selectionIndicatorYPos = viewBounds.y + viewBounds.height - selectionIndicatorHeight
        val selectionIndicatorWidth = viewBounds.width - paddingStart - paddingEnd
        val selectionIndicatorColor = if (wireframe is MobileSegment.Wireframe.TextWireframe) {
            wireframe.textStyle.color
        } else {
            SELECTED_TAB_INDICATOR_DEFAULT_COLOR
        }
        val selectionIndicatorShapeStyle = MobileSegment.ShapeStyle(
            backgroundColor = selectionIndicatorColor,
            opacity = view.alpha
        )
        return MobileSegment.Wireframe.ShapeWireframe(
            id = selectorId,
            x = selectionIndicatorXPos,
            y = selectionIndicatorYPos,
            width = selectionIndicatorWidth,
            height = selectionIndicatorHeight,
            shapeStyle = selectionIndicatorShapeStyle
        )
    }

    @UiThread
    private fun findAndResolveLabelWireframes(
        view: TabView,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback,
        internalLogger: InternalLogger
    ): List<MobileSegment.Wireframe> {
        for (i in 0 until view.childCount) {
            val viewChild = view.getChildAt(i) ?: continue

            @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
            val isTextView = TextView::class.java.isAssignableFrom(viewChild::class.java)
            if (isTextView) {
                return textViewMapper.map(
                    viewChild as TextView,
                    mappingContext,
                    asyncJobStatusCallback,
                    internalLogger
                )
            }
        }
        return emptyList()
    }

    companion object {
        internal const val SELECTED_TAB_INDICATOR_KEY_NAME = "selected_tab_indicator"
        internal const val SELECTED_TAB_INDICATOR_DEFAULT_COLOR = "#000000"
        internal const val SELECTED_TAB_INDICATOR_HEIGHT_IN_PX = 5L
    }
}
