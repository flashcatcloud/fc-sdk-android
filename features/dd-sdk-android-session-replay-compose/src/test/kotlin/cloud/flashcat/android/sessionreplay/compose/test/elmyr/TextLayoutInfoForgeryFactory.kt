/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.sessionreplay.compose.test.elmyr

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import cloud.flashcat.android.sessionreplay.compose.internal.mappers.semantics.TextLayoutInfo
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class TextLayoutInfoForgeryFactory : ForgeryFactory<TextLayoutInfo> {
    override fun getForgery(forge: Forge): TextLayoutInfo {
        return TextLayoutInfo(
            text = forge.aString(),
            color = forge.aLong().toULong(),
            fontSize = forge.aSmallInt().toLong(),
            fontFamily = forge.anElementFrom(
                listOf(
                    FontFamily.Serif,
                    FontFamily.SansSerif,
                    FontFamily.Cursive,
                    FontFamily.Monospace,
                    FontFamily.Default
                )
            ),
            textAlign = forge.anElementFrom(TextAlign.values())
        )
    }
}
