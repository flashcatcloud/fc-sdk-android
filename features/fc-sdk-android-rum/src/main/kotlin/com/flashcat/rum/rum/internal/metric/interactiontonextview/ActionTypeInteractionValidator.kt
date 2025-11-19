/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.rum.internal.metric.interactiontonextview

import com.flashcat.rum.rum.model.ActionEvent

internal class ActionTypeInteractionValidator : InteractionIngestionValidator {
    override fun validate(
        context: InternalInteractionContext
    ): Boolean {
        return context.actionType in ALLOWED_TYPES
    }

    companion object {
        private val ALLOWED_TYPES = setOf(
            ActionEvent.ActionEventActionType.TAP,
            ActionEvent.ActionEventActionType.SWIPE,
            ActionEvent.ActionEventActionType.CLICK,
            ActionEvent.ActionEventActionType.BACK
        )
    }
}
