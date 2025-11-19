/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sessionreplay.material.forge

import com.flashcat.rum.sessionreplay.utils.GlobalBounds
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class GlobalBoundsForgeryFactory : ForgeryFactory<GlobalBounds> {
    override fun getForgery(forge: Forge): GlobalBounds {
        return GlobalBounds(
            x = forge.aLong(),
            y = forge.aLong(),
            width = forge.aPositiveLong(),
            height = forge.aPositiveLong()
        )
    }
}
