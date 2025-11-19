/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.compose

/**
 * A [BackStackKeyResolver] that uses the [Any.hashCode] of the item as its stable key.
 *
 * @param T the type of item in the back stack.
 */
class HashcodeBackStackKeyResolver<T : Any> : BackStackKeyResolver<T> {

    override fun getStableKey(
        item: T
    ): String {
        return item.hashCode().toString()
    }
}
