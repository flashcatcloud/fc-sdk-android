/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.internal.flags

/**
 * Event for sending a RUM evaluation.
 * @param flagKey name of the flag.
 * @param value value of the flag.
 */
data class RumFlagEvaluationMessage(
    val flagKey: String,
    val value: Any
)
