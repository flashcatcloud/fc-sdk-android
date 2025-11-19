/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sessionreplay.forge

import com.flashcat.rum.sessionreplay.internal.processor.RecordedQueuedItemContext
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class RumContextDataForgeryFactory : ForgeryFactory<RecordedQueuedItemContext> {
    override fun getForgery(forge: Forge): RecordedQueuedItemContext {
        return RecordedQueuedItemContext(
            forge.aLong(),
            forge.getForgery()
        )
    }
}
