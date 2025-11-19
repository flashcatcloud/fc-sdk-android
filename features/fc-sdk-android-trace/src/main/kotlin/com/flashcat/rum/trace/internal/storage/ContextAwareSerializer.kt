/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.trace.internal.storage

import com.flashcat.rum.api.context.FlashcatContext

/**
 * A class which can transform an object of type [T] into a formatted String.
 */
internal interface ContextAwareSerializer<T : Any> {
    /**
     * Serializes the data into a String.
     * @return the String representing the data or null if any exception occurs
     */
    fun serialize(flashcatContext: FlashcatContext, model: T): String?
}
