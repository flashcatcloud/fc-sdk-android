/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sessionreplay.forge

import com.flashcat.rum.sessionreplay.model.MobileSegment
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class WireframeUpdateMutationForgeryFactory :
    ForgeryFactory<MobileSegment.WireframeUpdateMutation> {
    override fun getForgery(forge: Forge): MobileSegment.WireframeUpdateMutation {
        return when (forge.anInt(min = 0, max = 2)) {
            1 -> forge.getForgery<MobileSegment.WireframeUpdateMutation.TextWireframeUpdate>()
            else -> forge.getForgery<MobileSegment.WireframeUpdateMutation.ShapeWireframeUpdate>()
        }
    }
}
