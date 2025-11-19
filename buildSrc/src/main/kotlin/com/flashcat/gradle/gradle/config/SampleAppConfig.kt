/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.gradle.config

data class SampleAppConfig(
    val logsEndpoint: String = "",
    val tracesEndpoint: String = "",
    val rumEndpoint: String = "",
    val sessionReplayEndpoint: String = "",
    val token: String = "",
    val rumApplicationId: String = "",
    val apiKey: String = "",
    val applicationKey: String = ""
)
