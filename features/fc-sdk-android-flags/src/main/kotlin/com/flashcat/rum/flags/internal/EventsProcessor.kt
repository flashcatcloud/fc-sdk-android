/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.flags.internal

import com.flashcat.rum.flags.internal.model.PrecomputedFlag
import com.flashcat.rum.flags.model.EvaluationContext
import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation
internal interface EventsProcessor {
    fun processEvent(flagName: String, context: EvaluationContext, data: PrecomputedFlag)
}
