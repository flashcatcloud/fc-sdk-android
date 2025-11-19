/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.flashcat.rum.sessionreplay.compose

import androidx.compose.ui.platform.AndroidComposeView
import androidx.compose.ui.platform.ComposeView
import com.flashcat.rum.sessionreplay.ExtensionSupport
import com.flashcat.rum.sessionreplay.MapperTypeWrapper
import com.flashcat.rum.sessionreplay.compose.internal.mappers.semantics.AndroidComposeViewMapper
import com.flashcat.rum.sessionreplay.compose.internal.mappers.semantics.ComposeViewMapper
import com.flashcat.rum.sessionreplay.compose.internal.mappers.semantics.RootSemanticsNodeMapper
import com.flashcat.rum.sessionreplay.recorder.OptionSelectorDetector
import com.flashcat.rum.sessionreplay.utils.ColorStringFormatter
import com.flashcat.rum.sessionreplay.utils.DefaultColorStringFormatter
import com.flashcat.rum.sessionreplay.utils.DefaultViewBoundsResolver
import com.flashcat.rum.sessionreplay.utils.DefaultViewIdentifierResolver
import com.flashcat.rum.sessionreplay.utils.DrawableToColorMapper
import com.flashcat.rum.sessionreplay.utils.ViewBoundsResolver
import com.flashcat.rum.sessionreplay.utils.ViewIdentifierResolver

/**
 * Jetpack Compose extension support implementation to be used in the Session Replay
 * configuration.
 */
class ComposeExtensionSupport : ExtensionSupport {

    private val viewIdentifierResolver: ViewIdentifierResolver = DefaultViewIdentifierResolver
    private val colorStringFormatter: ColorStringFormatter = DefaultColorStringFormatter
    private val viewBoundsResolver: ViewBoundsResolver = DefaultViewBoundsResolver
    private val drawableToColorMapper: DrawableToColorMapper = DrawableToColorMapper.getDefault()
    private val rootSemanticsNodeMapper = RootSemanticsNodeMapper(colorStringFormatter)

    override fun getCustomViewMappers(): List<MapperTypeWrapper<*>> {
        return listOf(
            MapperTypeWrapper(
                ComposeView::class.java,
                ComposeViewMapper(
                    viewIdentifierResolver,
                    colorStringFormatter,
                    viewBoundsResolver,
                    drawableToColorMapper,
                    rootSemanticsNodeMapper = rootSemanticsNodeMapper
                )
            ),
            MapperTypeWrapper(
                AndroidComposeView::class.java,
                AndroidComposeViewMapper(
                    viewIdentifierResolver,
                    colorStringFormatter,
                    viewBoundsResolver,
                    drawableToColorMapper,
                    rootSemanticsNodeMapper
                )
            )
        )
    }

    override fun getOptionSelectorDetectors(): List<OptionSelectorDetector> {
        return emptyList()
    }

    override fun getCustomDrawableMapper(): List<DrawableToColorMapper> {
        return emptyList()
    }

    override fun name(): String =
        COMPOSE_EXTENSION_SUPPORT_NAME

    internal companion object {
        internal const val COMPOSE_EXTENSION_SUPPORT_NAME = "ComposeExtensionSupport"
    }
}
