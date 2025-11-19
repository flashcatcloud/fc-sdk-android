/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sessionreplay.forge

import com.flashcat.rum.sessionreplay.internal.net.ResourceEvent
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import java.util.UUID

internal class ResourceEventForgeryFactory : ForgeryFactory<ResourceEvent> {
    override fun getForgery(forge: Forge): ResourceEvent {
        return ResourceEvent(
            applicationId = forge.getForgery<UUID>().toString(),
            identifier = forge.getForgery<UUID>().toString(),
            resourceData = forge.aString().toByteArray()
        )
    }
}
