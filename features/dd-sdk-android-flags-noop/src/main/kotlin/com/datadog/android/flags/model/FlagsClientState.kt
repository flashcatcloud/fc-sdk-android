/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.model

/**
 * Represents the current operational state of a [FlagsClient].
 */
enum class FlagsClientState {
    /**
     * The client is initializing and not yet ready to evaluate flags.
     */
    INITIALIZING,

    /**
     * The client is ready to evaluate flags.
     */
    READY,

    /**
     * The client encountered a fatal error and cannot evaluate flags.
     */
    ERROR
}
