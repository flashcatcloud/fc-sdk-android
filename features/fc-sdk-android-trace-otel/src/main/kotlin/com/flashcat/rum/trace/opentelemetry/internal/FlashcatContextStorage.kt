/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.flashcat.rum.trace.opentelemetry.internal

import io.opentelemetry.context.Context
import io.opentelemetry.context.ContextStorage
import io.opentelemetry.context.Scope

internal class FlashcatContextStorage(private val wrapped: ContextStorage) : ContextStorage {
    override fun current(): Context {
        val current = wrapped.current() ?: Context.root()
        return if (current is OtelContext) {
            current
        } else {
            OtelContext(current)
        }
    }

    override fun attach(toAttach: Context): Scope {
        return wrapped.attach(toAttach)
    }
}
