/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sessionreplay.internal.resources

import com.flashcat.rum.core.persistence.Serializer
import com.flashcat.rum.sessionreplay.model.ResourceHashesEntry

internal class ResourceHashesEntrySerializer : Serializer<ResourceHashesEntry> {
    override fun serialize(model: ResourceHashesEntry): String {
        return model.toJson().toString()
    }
}
