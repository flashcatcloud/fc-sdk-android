/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.rum.integration.tests.elmyr

import cloud.flashcat.android.api.storage.RawBatchEvent
import cloud.flashcat.android.rum.integration.tests.utils.RumBatchEvent
import cloud.flashcat.android.rum.model.ActionEvent
import cloud.flashcat.android.rum.model.ErrorEvent
import cloud.flashcat.android.rum.model.LongTaskEvent
import cloud.flashcat.android.rum.model.ResourceEvent
import cloud.flashcat.android.rum.model.ViewEvent
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

class RumBatchEventForgeryFactory : ForgeryFactory<RumBatchEvent> {
    override fun getForgery(forge: Forge): RumBatchEvent {
        val jsonElement = forge.anElementFrom(
            forge.getForgery<ActionEvent>().toJson(),
            forge.getForgery<ErrorEvent>().toJson(),
            forge.getForgery<LongTaskEvent>().toJson(),
            forge.getForgery<ResourceEvent>().toJson(),
            forge.getForgery<ViewEvent>().toJson()
        )

        return RumBatchEvent(
            jsonElement,
            RawBatchEvent(
                jsonElement.toString().toByteArray()
            )
        )
    }
}
