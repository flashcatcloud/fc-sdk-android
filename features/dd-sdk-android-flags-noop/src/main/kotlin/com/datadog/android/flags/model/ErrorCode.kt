/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.model

/**
 * Standard error codes for feature flag resolution failures.
 */
enum class ErrorCode {
    /**
     * The flag could not be found.
     */
    FLAG_NOT_FOUND,

    /**
     * Error parsing the flag value.
     */
    PARSE_ERROR,

    /**
     * The flag type doesn't match the expected type.
     */
    TYPE_MISMATCH,

    /**
     * No targeting key was provided.
     */
    TARGETING_KEY_MISSING,

    /**
     * The evaluation context is invalid.
     */
    INVALID_CONTEXT,

    /**
     * The provider is not yet ready.
     */
    PROVIDER_NOT_READY,

    /**
     * The provider encountered a fatal error.
     */
    PROVIDER_FATAL,

    /**
     * A general error occurred.
     */
    GENERAL
}
