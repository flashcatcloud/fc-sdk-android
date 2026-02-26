/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.openfeature

import com.datadog.android.flags.FlagsClient
import dev.openfeature.kotlin.sdk.FeatureProvider

/**
 * Extension function to convert a [FlagsClient] to an OpenFeature [FeatureProvider].
 *
 * @return An OpenFeature [FeatureProvider] implementation.
 */
fun FlagsClient.asOpenFeatureProvider(): FeatureProvider = DatadogFlagsProvider.wrap(this)
