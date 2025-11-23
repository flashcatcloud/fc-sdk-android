/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package com.datadog.android.flags.internal

import com.flashcat.android.api.feature.FeatureScope
import com.datadog.android.internal.flags.RumFlagEvaluationMessage
import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation
internal interface RumEvaluationLogger {
    fun logEvaluation(flagKey: String, value: Any)
}

internal class DefaultRumEvaluationLogger(private val featureScope: FeatureScope) : RumEvaluationLogger {
    override fun logEvaluation(flagKey: String, value: Any) {
        featureScope.sendEvent(
            RumFlagEvaluationMessage(
                flagKey = flagKey,
                value = value
            )
        )
    }
}
