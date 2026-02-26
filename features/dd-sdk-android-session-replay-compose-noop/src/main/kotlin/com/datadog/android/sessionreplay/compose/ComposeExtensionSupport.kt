/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose

import com.datadog.android.sessionreplay.ExtensionSupport
import com.datadog.android.sessionreplay.MapperTypeWrapper

/**
 * [ExtensionSupport] implementation for Compose.
 */
@Suppress("UNUSED_PARAMETER")
class ComposeExtensionSupport : ExtensionSupport {
    override fun getCustomViewMappers(): List<MapperTypeWrapper<*>> {
        return emptyList()
    }

    override fun getCustomDrawableMapper(): List<Any> {
        return emptyList()
    }

    override fun getOptionSelectorDetectors(): List<Any> {
        return emptyList()
    }

    override fun name(): String = ""
}
