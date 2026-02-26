/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.model

/**
 * Defines the context used for evaluating feature flags and experiments.
 *
 * @param targetingKey A unique identifier for the entity being evaluated (e.g., user ID, device ID).
 * @param attributes Additional attributes to use for targeting (e.g., email, plan, version).
 */
data class EvaluationContext(
    val targetingKey: String,
    val attributes: Map<String, String> = emptyMap()
)
