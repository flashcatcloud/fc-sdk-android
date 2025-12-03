/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.sessionreplay.compose.internal.mappers.semantics

import androidx.annotation.UiThread
import androidx.compose.ui.platform.ComposeView
import cloud.flashcat.android.api.InternalLogger
import cloud.flashcat.android.sessionreplay.compose.internal.utils.SemanticsUtils
import cloud.flashcat.android.sessionreplay.model.MobileSegment
import cloud.flashcat.android.sessionreplay.recorder.MappingContext
import cloud.flashcat.android.sessionreplay.recorder.mapper.BaseWireframeMapper
import cloud.flashcat.android.sessionreplay.utils.AsyncJobStatusCallback
import cloud.flashcat.android.sessionreplay.utils.ColorStringFormatter
import cloud.flashcat.android.sessionreplay.utils.DrawableToColorMapper
import cloud.flashcat.android.sessionreplay.utils.ViewBoundsResolver
import cloud.flashcat.android.sessionreplay.utils.ViewIdentifierResolver

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
