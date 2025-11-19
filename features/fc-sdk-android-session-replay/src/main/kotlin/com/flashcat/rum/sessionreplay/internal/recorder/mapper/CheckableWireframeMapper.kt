/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sessionreplay.internal.recorder.mapper

import android.view.View
import android.widget.Checkable
import androidx.annotation.UiThread
import com.flashcat.rum.api.InternalLogger
import com.flashcat.rum.sessionreplay.ImagePrivacy
import com.flashcat.rum.sessionreplay.TextAndInputPrivacy
import com.flashcat.rum.sessionreplay.model.MobileSegment
import com.flashcat.rum.sessionreplay.recorder.MappingContext
import com.flashcat.rum.sessionreplay.recorder.mapper.BaseWireframeMapper
import com.flashcat.rum.sessionreplay.utils.AsyncJobStatusCallback
import com.flashcat.rum.sessionreplay.utils.ColorStringFormatter
import com.flashcat.rum.sessionreplay.utils.DrawableToColorMapper
import com.flashcat.rum.sessionreplay.utils.ViewBoundsResolver
import com.flashcat.rum.sessionreplay.utils.ViewIdentifierResolver

internal abstract class CheckableWireframeMapper<T>(
    viewIdentifierResolver: ViewIdentifierResolver,
    colorStringFormatter: ColorStringFormatter,
    viewBoundsResolver: ViewBoundsResolver,
    drawableToColorMapper: DrawableToColorMapper
) : BaseWireframeMapper<T>(
    viewIdentifierResolver,
    colorStringFormatter,
    viewBoundsResolver,
    drawableToColorMapper
) where T : View, T : Checkable {

    @UiThread
    override fun map(
        view: T,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback,
        internalLogger: InternalLogger
    ): List<MobileSegment.Wireframe> {
        val mainWireframes = resolveMainWireframes(view, mappingContext, asyncJobStatusCallback, internalLogger)
        val checkableWireframes = if (mappingContext.textAndInputPrivacy != TextAndInputPrivacy.MASK_SENSITIVE_INPUTS) {
            resolveMaskedCheckable(view, mappingContext)
        } else {
            // Resolves checkable view regardless the state
            resolveCheckable(view, mappingContext, asyncJobStatusCallback)
        }
        checkableWireframes?.let { wireframes ->
            return mainWireframes + wireframes
        }
        return mainWireframes
    }

    protected fun mapInputPrivacyToImagePrivacy(inputPrivacy: TextAndInputPrivacy): ImagePrivacy {
        return when (inputPrivacy) {
            TextAndInputPrivacy.MASK_SENSITIVE_INPUTS -> ImagePrivacy.MASK_NONE
            TextAndInputPrivacy.MASK_ALL_INPUTS,
            TextAndInputPrivacy.MASK_ALL -> ImagePrivacy.MASK_ALL
        }
    }

    @UiThread
    abstract fun resolveMainWireframes(
        view: T,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback,
        internalLogger: InternalLogger
    ): List<MobileSegment.Wireframe>

    @UiThread
    abstract fun resolveMaskedCheckable(
        view: T,
        mappingContext: MappingContext
    ): List<MobileSegment.Wireframe>?

    @UiThread
    abstract fun resolveCheckable(
        view: T,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): List<MobileSegment.Wireframe>
}
