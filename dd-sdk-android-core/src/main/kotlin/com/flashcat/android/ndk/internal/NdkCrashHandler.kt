/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.  * Modified 2025 by FlashCat, Inc.
 */

package com.flashcat.android.ndk.internal

import com.flashcat.android.api.feature.FeatureSdkCore
import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation
internal interface NdkCrashHandler {

    fun prepareData()

    fun handleNdkCrash(sdkCore: FeatureSdkCore)
}
