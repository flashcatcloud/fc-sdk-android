/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.rum.utils.forge

import com.flashcat.rum.rum.internal.metric.interactiontonextview.InternalInteractionContext
import com.flashcat.rum.rum.model.ActionEvent
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import java.util.UUID

internal class InternalInteractionContextFactory : ForgeryFactory<InternalInteractionContext> {
    override fun getForgery(forge: Forge): InternalInteractionContext {
        return InternalInteractionContext(
            viewId = forge.getForgery<UUID>().toString(),
            actionType = forge.aValueFrom(ActionEvent.ActionEventActionType::class.java),
            eventCreatedAtNanos = forge.aPositiveLong()
        )
    }
}
