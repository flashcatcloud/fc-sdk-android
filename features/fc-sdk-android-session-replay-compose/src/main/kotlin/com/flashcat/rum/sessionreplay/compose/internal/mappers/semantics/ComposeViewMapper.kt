/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sessionreplay.compose.internal.mappers.semantics

import androidx.annotation.UiThread
import androidx.compose.ui.platform.ComposeView
import com.flashcat.rum.api.InternalLogger
import com.flashcat.rum.sessionreplay.compose.internal.utils.SemanticsUtils
import com.flashcat.rum.sessionreplay.model.MobileSegment
import com.flashcat.rum.sessionreplay.recorder.MappingContext
import com.flashcat.rum.sessionreplay.recorder.mapper.BaseWireframeMapper
import com.flashcat.rum.sessionreplay.utils.AsyncJobStatusCallback
import com.flashcat.rum.sessionreplay.utils.ColorStringFormatter
import com.flashcat.rum.sessionreplay.utils.DrawableToColorMapper
import com.flashcat.rum.sessionreplay.utils.ViewBoundsResolver
import com.flashcat.rum.sessionreplay.utils.ViewIdentifierResolver

internal class ComposeViewMapper(
    viewIdentifierResolver: ViewIdentifierResolver,
    colorStringFormatter: ColorStringFormatter,
    viewBoundsResolver: ViewBoundsResolver,
    drawableToColorMapper: DrawableToColorMapper,
    private val semanticsUtils: SemanticsUtils = SemanticsUtils(),
    private val rootSemanticsNodeMapper: RootSemanticsNodeMapper
) : BaseWireframeMapper<ComposeView>(
    viewIdentifierResolver,
    colorStringFormatter,
    viewBoundsResolver,
    drawableToColorMapper
) {
    @UiThread
    override fun map(
        view: ComposeView,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback,
        internalLogger: InternalLogger
    ): List<MobileSegment.Wireframe> {
        val density =
            mappingContext.systemInformation.screenDensity.let { if (it == 0.0f) 1.0f else it }
        return semanticsUtils.findRootSemanticsNode(view)?.let { node ->
            rootSemanticsNodeMapper.createComposeWireframes(
                node,
                density,
                mappingContext,
                asyncJobStatusCallback
            )
        } ?: emptyList()
    }
}
