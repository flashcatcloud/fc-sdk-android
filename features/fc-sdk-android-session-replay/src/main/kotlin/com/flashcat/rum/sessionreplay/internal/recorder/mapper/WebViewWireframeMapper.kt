/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sessionreplay.internal.recorder.mapper

import android.webkit.WebView
import androidx.annotation.UiThread
import com.flashcat.rum.api.InternalLogger
import com.flashcat.rum.sessionreplay.model.MobileSegment
import com.flashcat.rum.sessionreplay.recorder.MappingContext
import com.flashcat.rum.sessionreplay.recorder.mapper.BaseWireframeMapper
import com.flashcat.rum.sessionreplay.utils.AsyncJobStatusCallback
import com.flashcat.rum.sessionreplay.utils.ColorStringFormatter
import com.flashcat.rum.sessionreplay.utils.DrawableToColorMapper
import com.flashcat.rum.sessionreplay.utils.ViewBoundsResolver
import com.flashcat.rum.sessionreplay.utils.ViewIdentifierResolver

internal class WebViewWireframeMapper(
    viewIdentifierResolver: ViewIdentifierResolver,
    colorStringFormatter: ColorStringFormatter,
    viewBoundsResolver: ViewBoundsResolver,
    drawableToColorMapper: DrawableToColorMapper
) : BaseWireframeMapper<WebView>(
    viewIdentifierResolver,
    colorStringFormatter,
    viewBoundsResolver,
    drawableToColorMapper
) {

    @UiThread
    override fun map(
        view: WebView,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback,
        internalLogger: InternalLogger
    ): List<MobileSegment.Wireframe> {
        val viewGlobalBounds = viewBoundsResolver.resolveViewGlobalBounds(
            view,
            mappingContext.systemInformation.screenDensity
        )
        val webViewId = resolveViewId(view)
        return listOf(
            MobileSegment.Wireframe.WebviewWireframe(
                webViewId,
                viewGlobalBounds.x,
                viewGlobalBounds.y,
                viewGlobalBounds.width,
                viewGlobalBounds.height,
                slotId = webViewId.toString(),
                isVisible = true
            )
        )
    }
}
