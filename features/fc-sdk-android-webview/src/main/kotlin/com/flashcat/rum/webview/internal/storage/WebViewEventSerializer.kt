/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.webview.internal.storage

import com.flashcat.rum.core.persistence.Serializer
import com.google.gson.JsonObject

internal class WebViewEventSerializer :
    Serializer<JsonObject> {

    override fun serialize(model: JsonObject): String {
        return model.toString()
    }
}
