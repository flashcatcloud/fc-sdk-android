/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.rum.utils.forge

import com.flashcat.rum.rum.metric.interactiontonextview.PreviousViewLastInteractionContext
import com.flashcat.rum.rum.model.ActionEvent
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class PreviousViewLastActionContextFactory : ForgeryFactory<PreviousViewLastInteractionContext> {
    override fun getForgery(forge: Forge): PreviousViewLastInteractionContext {
        return PreviousViewLastInteractionContext(
            actionType = forge.aValueFrom(ActionEvent.ActionEventActionType::class.java),
            eventCreatedAtNanos = forge.aPositiveLong(),
            currentViewCreationTimestamp = forge.aNullable { forge.aPositiveLong() }
        )
    }
}
