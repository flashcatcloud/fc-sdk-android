/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.model

/**
 * Contains detailed information about a feature flag resolution.
 *
 * @param T The type of the flag value.
 * @property value The resolved flag value.
 * @property variant Optional identifier for the resolved variant.
 * @property reason Optional explanation of why this value was resolved.
 * @property errorCode Optional error code (null indicates success).
 * @property errorMessage Optional human-readable error message.
 * @property flagMetadata Optional metadata associated with the flag.
 */
data class ResolutionDetails<T>(
    val value: T,
    val variant: String? = null,
    val reason: ResolutionReason? = null,
    val errorCode: ErrorCode? = null,
    val errorMessage: String? = null,
    val flagMetadata: Map<String, Any>? = null
)
