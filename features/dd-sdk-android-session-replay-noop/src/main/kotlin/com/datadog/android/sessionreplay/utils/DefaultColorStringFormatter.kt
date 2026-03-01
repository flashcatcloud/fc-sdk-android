/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.utils


/**
 * String utility methods needed in the Session Replay Wireframe Mappers.
 * This class is meant for internal usage so please use it with careful as it might change in time.
 */
object DefaultColorStringFormatter : ColorStringFormatter {

    override fun formatColorAsHexString(color: Int): String {
        // shift Android's ARGB to Web RGBA
        return ""
    }

    override fun formatColorAndAlphaAsHexString(color: Int, alpha: Int): String {
        return ""
    }
}
