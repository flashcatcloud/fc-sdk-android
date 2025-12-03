/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.core.internal.account

import cloud.flashcat.tools.annotation.NoOpImplementation

@NoOpImplementation
internal interface MutableAccountInfoProvider : AccountInfoProvider {

    fun setAccountInfo(
        id: String,
        name: String?,
        extraInfo: Map<String, Any?>
    )

    fun addExtraInfo(extraInfo: Map<String, Any?>)

    fun clearAccountInfo()
}
