/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.rum.internal.collections

import com.flashcat.rum.internal.collections.EvictingQueue
import java.util.Queue

fun <E> List<E>.toEvictingQueue(maxSize: Int = size * 2): Queue<E> {
    val queue = EvictingQueue<E>(maxSize)
    forEach { queue.add(it) }
    return queue
}
