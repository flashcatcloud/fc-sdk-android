/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sdk.utils

internal fun String.isTracesUrl(): Boolean {
    return this.matches(Regex("(.*)/traces(.*)"))
}

internal fun String.isLogsUrl(): Boolean {
    return this.matches(Regex("(.*)/logs(.*)"))
}

internal fun String.isRumUrl(): Boolean {
    return this.matches(Regex("(.*)/rum(.*)"))
}
