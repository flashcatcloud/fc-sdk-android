/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.sessionreplay.compose.internal.mappers.semantics

import androidx.compose.ui.semantics.SemanticsNode
import cloud.flashcat.android.sessionreplay.compose.internal.data.SemanticsWireframe
import cloud.flashcat.android.sessionreplay.compose.internal.data.UiContext
import cloud.flashcat.android.sessionreplay.compose.internal.utils.SemanticsUtils
import cloud.flashcat.android.sessionreplay.utils.AsyncJobStatusCallback
import cloud.flashcat.android.sessionreplay.utils.ColorStringFormatter

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
