/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.sample.data

internal sealed class Result {
    class Success<out T>(val data: T) : Result()
    class Failure(val message: String? = null, val throwable: Throwable? = null) : Result()
}
