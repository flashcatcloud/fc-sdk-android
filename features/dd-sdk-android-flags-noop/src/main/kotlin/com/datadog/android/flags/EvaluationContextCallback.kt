/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

import com.datadog.android.flags.model.EvaluationContext

/**
 * Callback interface for notifying when an [EvaluationContext] update operation completes.
 */
interface EvaluationContextCallback {
    /**
     * Called when the context update and associated flag fetching complete successfully.
     * @param context The context that was applied.
     */
    fun onSuccess(context: EvaluationContext)

    /**
     * Called when the context update or flag fetching operation fails.
     * @param context The context that failed to apply.
     * @param error The error that occurred.
     */
    fun onError(context: EvaluationContext, error: Throwable)
}
