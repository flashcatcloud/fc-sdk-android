/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.sessionreplay.forge

import cloud.flashcat.android.sessionreplay.internal.processor.WireframeBounds
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class WireframeBoundsForgeryFactory : ForgeryFactory<WireframeBounds> {
    override fun getForgery(forge: Forge): WireframeBounds {
        val left = forge.aLong(min = 0, max = 1000)
        val right = left + forge.aLong(min = 1, max = 1000)
        val top = forge.aLong(min = 0, max = 1000)
        val bottom = top + forge.aLong(min = 1, max = 1000)
        return WireframeBounds(
            left = left,
            right = right,
            top = top,
            bottom = bottom,
            width = forge.aPositiveLong(),
            height = forge.aPositiveLong()
        )
    }
}
