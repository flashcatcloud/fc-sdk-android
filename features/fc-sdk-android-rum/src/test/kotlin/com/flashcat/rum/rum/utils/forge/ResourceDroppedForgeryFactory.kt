/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.rum.utils.forge

import com.flashcat.rum.rum.internal.domain.scope.RumRawEvent
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import java.util.UUID

internal class ResourceDroppedForgeryFactory : ForgeryFactory<RumRawEvent.ResourceDropped> {
    override fun getForgery(forge: Forge): RumRawEvent.ResourceDropped {
        return RumRawEvent.ResourceDropped(
            viewId = forge.getForgery<UUID>().toString(),
            resourceId = forge.getForgery<UUID>().toString()
        )
    }
}
