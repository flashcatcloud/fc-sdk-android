/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.utils.forge

import cloud.flashcat.android.rum.model.ActionEvent
import cloud.flashcat.android.rum.model.ErrorEvent
import cloud.flashcat.android.rum.model.LongTaskEvent
import cloud.flashcat.android.rum.model.ResourceEvent
import cloud.flashcat.android.rum.model.ViewEvent
import com.google.gson.JsonObject
import fr.xgouchet.elmyr.Forge

internal fun Forge.aRumEventAsJson(): JsonObject {
    return anElementFrom(
        this.getForgery<ViewEvent>().toJson().asJsonObject,
        this.getForgery<LongTaskEvent>().toJson().asJsonObject,
        this.getForgery<ActionEvent>().toJson().asJsonObject,
        this.getForgery<ResourceEvent>().toJson().asJsonObject,
        this.getForgery<ErrorEvent>().toJson().asJsonObject
    )
}
