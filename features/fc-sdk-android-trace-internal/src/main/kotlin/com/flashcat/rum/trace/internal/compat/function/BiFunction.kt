package com.flashcat.rum.trace.internal.compat.function

internal interface BiFunction<T, U, R> {

    fun apply(t: T, u: U): R
}
