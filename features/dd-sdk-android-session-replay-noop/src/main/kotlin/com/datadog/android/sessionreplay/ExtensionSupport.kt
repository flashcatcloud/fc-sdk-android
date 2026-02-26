/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

/**
 * Interface to be implemented by any Session Replay extension.
 */
interface ExtensionSupport {
    /**
     * @return the list of [MapperTypeWrapper] to use for this extension.
     */
    fun getCustomViewMappers(): List<MapperTypeWrapper<*>>

    /**
     * @return the list of [Any] to use for this extension.
     */
    fun getCustomDrawableMapper(): List<Any>

    /**
     * @return the list of [Any] to use for this extension.
     */
    fun getOptionSelectorDetectors(): List<Any>

    /**
     * @return the name of the extension.
     */
    fun name(): String
}
