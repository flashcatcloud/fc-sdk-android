/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.okhttp.internal.rum

import cloud.flashcat.android.okhttp.internal.utils.identifyRequest
import cloud.flashcat.android.rum.resource.ResourceId
import okhttp3.Request
import java.util.UUID

internal fun Request.buildResourceId(generateUuid: Boolean): ResourceId {
    val uuid = tag(UUID::class.java) ?: (if (generateUuid) UUID.randomUUID() else null)
    val key = identifyRequest(this)

    return ResourceId(key, uuid?.toString())
}
