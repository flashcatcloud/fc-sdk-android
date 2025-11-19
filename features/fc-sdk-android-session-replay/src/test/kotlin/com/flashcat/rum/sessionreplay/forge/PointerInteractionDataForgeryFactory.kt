/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sessionreplay.forge

import com.flashcat.rum.sessionreplay.model.MobileSegment
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class PointerInteractionDataForgeryFactory :
    ForgeryFactory<MobileSegment.MobileIncrementalData.PointerInteractionData> {
    override fun getForgery(forge: Forge): MobileSegment.MobileIncrementalData.PointerInteractionData {
        return MobileSegment.MobileIncrementalData.PointerInteractionData(
            pointerEventType = forge.aValueFrom(MobileSegment.PointerEventType::class.java),
            pointerType = forge.aValueFrom(MobileSegment.PointerType::class.java),
            pointerId = forge.aPositiveLong(),
            x = forge.aLong(),
            y = forge.aLong()
        )
    }
}
