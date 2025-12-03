/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.webview.internal.rum.domain

import java.util.UUID

internal data class RumContext(
    val applicationId: String,
    val sessionId: String,
    val sessionState: String
) {

    companion object {
        val NULL_UUID = UUID(0, 0).toString()
    }
}
