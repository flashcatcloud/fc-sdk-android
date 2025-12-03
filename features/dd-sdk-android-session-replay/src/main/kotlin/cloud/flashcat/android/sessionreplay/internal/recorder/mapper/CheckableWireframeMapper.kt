/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.sessionreplay.internal.recorder.mapper

import android.view.View
import android.widget.Checkable
import androidx.annotation.UiThread
import cloud.flashcat.android.api.InternalLogger
import cloud.flashcat.android.sessionreplay.ImagePrivacy
import cloud.flashcat.android.sessionreplay.TextAndInputPrivacy
import cloud.flashcat.android.sessionreplay.model.MobileSegment
import cloud.flashcat.android.sessionreplay.recorder.MappingContext
import cloud.flashcat.android.sessionreplay.recorder.mapper.BaseWireframeMapper
import cloud.flashcat.android.sessionreplay.utils.AsyncJobStatusCallback
import cloud.flashcat.android.sessionreplay.utils.ColorStringFormatter
import cloud.flashcat.android.sessionreplay.utils.DrawableToColorMapper
import cloud.flashcat.android.sessionreplay.utils.ViewBoundsResolver
import cloud.flashcat.android.sessionreplay.utils.ViewIdentifierResolver

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
