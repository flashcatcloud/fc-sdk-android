/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sessionreplay.recorder

import android.content.res.Configuration
import com.flashcat.rum.sessionreplay.utils.GlobalBounds

/**
 * Provides information about the current system.
 * @param screenBounds the screen bounds in Global coordinates
 * @param screenOrientation the current screen orientation
 * @param screenDensity current screen density
 * @param themeColor application theme color
 */
data class SystemInformation(
    val screenBounds: GlobalBounds,
    val screenOrientation: Int = Configuration.ORIENTATION_UNDEFINED,
    val screenDensity: Float,
    val themeColor: String? = null
)
