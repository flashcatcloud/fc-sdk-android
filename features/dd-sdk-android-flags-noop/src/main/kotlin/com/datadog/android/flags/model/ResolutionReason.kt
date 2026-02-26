/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.model

/**
 * Reason codes explaining why a particular flag value was resolved.
 */
enum class ResolutionReason {
    STATIC,
    DEFAULT,
    TARGETING_MATCH,
    RULE_MATCH,
    PREREQUISITE_FAILED,
    ERROR
}
