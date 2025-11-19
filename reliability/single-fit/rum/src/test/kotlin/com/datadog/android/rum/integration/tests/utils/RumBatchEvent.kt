/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.rum.integration.tests.utils

import com.flashcat.rum.api.storage.RawBatchEvent
import com.google.gson.JsonElement

data class RumBatchEvent(
    val rumEvent: JsonElement,
    val batchEvent: RawBatchEvent
)
