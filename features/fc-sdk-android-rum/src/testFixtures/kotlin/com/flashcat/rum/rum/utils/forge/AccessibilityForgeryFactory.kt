/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.rum.utils.forge

import com.flashcat.rum.rum.model.ViewEvent.Accessibility
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class AccessibilityForgeryFactory : ForgeryFactory<Accessibility> {
    override fun getForgery(forge: Forge): Accessibility {
        return Accessibility(
            textSize = forge.aNullable { aString() },
            rtlEnabled = forge.aNullable { aBool() },
            screenReaderEnabled = forge.aNullable { aBool() },
            increaseContrastEnabled = forge.aNullable { aBool() },
            reducedAnimationsEnabled = forge.aNullable { aBool() },
            invertColorsEnabled = forge.aNullable { aBool() },
            singleAppModeEnabled = forge.aNullable { aBool() }
        )
    }
}
