/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.sessionreplay.material

import androidx.cardview.widget.CardView
import cloud.flashcat.android.sessionreplay.ExtensionSupport
import cloud.flashcat.android.sessionreplay.MapperTypeWrapper
import cloud.flashcat.android.sessionreplay.material.internal.CardWireframeMapper
import cloud.flashcat.android.sessionreplay.material.internal.ChipWireframeMapper
import cloud.flashcat.android.sessionreplay.material.internal.MaterialDrawableToColorMapper
import cloud.flashcat.android.sessionreplay.material.internal.MaterialOptionSelectorDetector
import cloud.flashcat.android.sessionreplay.material.internal.SliderWireframeMapper
import cloud.flashcat.android.sessionreplay.material.internal.TabWireframeMapper
import cloud.flashcat.android.sessionreplay.recorder.OptionSelectorDetector
import cloud.flashcat.android.sessionreplay.recorder.mapper.TextViewMapper
import cloud.flashcat.android.sessionreplay.utils.ColorStringFormatter
import cloud.flashcat.android.sessionreplay.utils.DefaultColorStringFormatter
import cloud.flashcat.android.sessionreplay.utils.DefaultViewBoundsResolver
import cloud.flashcat.android.sessionreplay.utils.DefaultViewIdentifierResolver
import cloud.flashcat.android.sessionreplay.utils.DrawableToColorMapper
import cloud.flashcat.android.sessionreplay.utils.ViewBoundsResolver
import cloud.flashcat.android.sessionreplay.utils.ViewIdentifierResolver
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
