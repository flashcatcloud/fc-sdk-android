/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.internal.utils

import java.math.BigInteger

/**
 * Radix used to convert numbers to hexadecimal strings.
 */
private const val HEX_RADIX: Int = 16

/**
 * Converts this [Int] into hexadecimal representation.
 */
fun Int.toHexString(): String = toString(HEX_RADIX)

/**
 * Converts this [Long] into hexadecimal representation.
 */
fun Long.toHexString(): String = toString(HEX_RADIX)

/**
 * Converts this [BigInteger] into hexadecimal representation.
 */
fun BigInteger.toHexString(): String {
    return toLong().toHexString()
}
