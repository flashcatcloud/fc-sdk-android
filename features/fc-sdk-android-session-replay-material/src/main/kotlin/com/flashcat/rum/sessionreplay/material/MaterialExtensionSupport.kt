/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sessionreplay.material

import androidx.cardview.widget.CardView
import com.flashcat.rum.sessionreplay.ExtensionSupport
import com.flashcat.rum.sessionreplay.MapperTypeWrapper
import com.flashcat.rum.sessionreplay.material.internal.CardWireframeMapper
import com.flashcat.rum.sessionreplay.material.internal.ChipWireframeMapper
import com.flashcat.rum.sessionreplay.material.internal.MaterialDrawableToColorMapper
import com.flashcat.rum.sessionreplay.material.internal.MaterialOptionSelectorDetector
import com.flashcat.rum.sessionreplay.material.internal.SliderWireframeMapper
import com.flashcat.rum.sessionreplay.material.internal.TabWireframeMapper
import com.flashcat.rum.sessionreplay.recorder.OptionSelectorDetector
import com.flashcat.rum.sessionreplay.recorder.mapper.TextViewMapper
import com.flashcat.rum.sessionreplay.utils.ColorStringFormatter
import com.flashcat.rum.sessionreplay.utils.DefaultColorStringFormatter
import com.flashcat.rum.sessionreplay.utils.DefaultViewBoundsResolver
import com.flashcat.rum.sessionreplay.utils.DefaultViewIdentifierResolver
import com.flashcat.rum.sessionreplay.utils.DrawableToColorMapper
import com.flashcat.rum.sessionreplay.utils.ViewBoundsResolver
import com.flashcat.rum.sessionreplay.utils.ViewIdentifierResolver
import com.google.android.material.chip.Chip
import com.google.android.material.slider.Slider
import com.google.android.material.tabs.TabLayout

/**
 * Android Material extension support implementation to be used in the Session Replay
 * configuration.
 */
class MaterialExtensionSupport : ExtensionSupport {

    private val viewIdentifierResolver: ViewIdentifierResolver = DefaultViewIdentifierResolver
    private val colorStringFormatter: ColorStringFormatter = DefaultColorStringFormatter
    private val viewBoundsResolver: ViewBoundsResolver = DefaultViewBoundsResolver
    private val materialDrawableToColorMapper = MaterialDrawableToColorMapper()
    private val drawableToColorMapper: DrawableToColorMapper =
        DrawableToColorMapper.getDefault(listOf(materialDrawableToColorMapper))

    override fun getCustomViewMappers(): List<MapperTypeWrapper<*>> {
        val sliderWireframeMapper = SliderWireframeMapper(
            viewIdentifierResolver,
            colorStringFormatter,
            viewBoundsResolver
        )

        val tabWireframeMapper = TabWireframeMapper(
            viewIdentifierResolver,
            viewBoundsResolver,
            TextViewMapper(
                viewIdentifierResolver,
                colorStringFormatter,
                viewBoundsResolver,
                drawableToColorMapper
            )
        )

        val cardWireframeMapper = CardWireframeMapper(
            viewIdentifierResolver,
            colorStringFormatter,
            viewBoundsResolver,
            drawableToColorMapper
        )
        val chipWireframeMapper = ChipWireframeMapper(
            viewIdentifierResolver,
            colorStringFormatter,
            viewBoundsResolver,
            drawableToColorMapper
        )

        return listOf(
            MapperTypeWrapper(Slider::class.java, sliderWireframeMapper),
            MapperTypeWrapper(TabLayout.TabView::class.java, tabWireframeMapper),
            MapperTypeWrapper(CardView::class.java, cardWireframeMapper),
            MapperTypeWrapper(Chip::class.java, chipWireframeMapper)
        )
    }

    override fun getOptionSelectorDetectors(): List<OptionSelectorDetector> {
        return listOf(MaterialOptionSelectorDetector())
    }

    override fun getCustomDrawableMapper(): List<DrawableToColorMapper> {
        return listOf(materialDrawableToColorMapper)
    }

    override fun name(): String =
        MATERIAL_EXTENSION_SUPPORT_NAME

    internal companion object {
        internal const val MATERIAL_EXTENSION_SUPPORT_NAME = "MaterialExtensionSupport"
    }
}
