package com.flashcat.rum.trace.internal.compat.function

internal interface Function<T, R> {

    fun apply(t: T): R
}
