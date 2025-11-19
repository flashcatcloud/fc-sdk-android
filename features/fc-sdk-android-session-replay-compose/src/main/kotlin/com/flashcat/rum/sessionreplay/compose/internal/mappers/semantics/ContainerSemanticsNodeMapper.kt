/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sessionreplay.compose.internal.mappers.semantics

import androidx.compose.ui.semantics.SemanticsNode
import com.flashcat.rum.sessionreplay.compose.internal.data.SemanticsWireframe
import com.flashcat.rum.sessionreplay.compose.internal.data.UiContext
import com.flashcat.rum.sessionreplay.compose.internal.utils.SemanticsUtils
import com.flashcat.rum.sessionreplay.utils.AsyncJobStatusCallback
import com.flashcat.rum.sessionreplay.utils.ColorStringFormatter

internal class ContainerSemanticsNodeMapper(
    colorStringFormatter: ColorStringFormatter,
    private val semanticsUtils: SemanticsUtils = SemanticsUtils()
) : AbstractSemanticsNodeMapper(colorStringFormatter, semanticsUtils) {
    override fun map(
        semanticsNode: SemanticsNode,
        parentContext: UiContext,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): SemanticsWireframe {
        val wireframes = resolveModifierWireframes(semanticsNode)
        val backgroundColor = semanticsUtils.resolveBackgroundColor(semanticsNode)?.let {
            convertColor(it)
        }
        val textAndInputPrivacy = semanticsUtils.getTextAndInputPrivacyOverride(semanticsNode)
            ?: parentContext.textAndInputPrivacy
        val imagePrivacy = semanticsUtils.getImagePrivacyOverride(semanticsNode)
            ?: parentContext.imagePrivacy
        return SemanticsWireframe(
            wireframes = wireframes,
            uiContext = parentContext.copy(
                parentContentColor = backgroundColor ?: parentContext.parentContentColor,
                imagePrivacy = imagePrivacy,
                textAndInputPrivacy = textAndInputPrivacy
            )
        )
    }
}
