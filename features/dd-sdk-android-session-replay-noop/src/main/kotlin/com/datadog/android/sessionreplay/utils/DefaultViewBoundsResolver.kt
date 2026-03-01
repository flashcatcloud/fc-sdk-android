/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.utils

import android.view.View

/**
 * View utility methods needed in the Session Replay Wireframe Mappers.
 * This class is meant for internal usage so please use it with careful as it might change in time.
 */
object DefaultViewBoundsResolver : ViewBoundsResolver {

    override fun resolveViewGlobalBounds(view: View, screenDensity: Float): GlobalBounds {
        return GlobalBounds(x = 0, y = 0, width = 0, height = 0)
    }

    override fun resolveViewPaddedBounds(view: View, screenDensity: Float): GlobalBounds {
        return GlobalBounds(x = 0, y = 0, width = 0, height = 0)
    }
}
