/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.webview.internal.rum

import com.flashcat.rum.api.InternalLogger
import com.flashcat.rum.api.context.FlashcatContext
import com.flashcat.rum.api.feature.Feature
import com.flashcat.rum.webview.internal.rum.domain.RumContext

internal class WebViewRumEventContextProvider(private val internalLogger: InternalLogger) {

    private var rumFeatureDisabled = false

    @Suppress("ComplexCondition")
    fun getRumContext(flashcatContext: FlashcatContext): RumContext? {
        if (rumFeatureDisabled) {
            return null
        }

        val rumContext = flashcatContext.featuresContext[Feature.RUM_FEATURE_NAME]
        val rumApplicationId = rumContext?.get("application_id") as? String
        val rumSessionId = rumContext?.get("session_id") as? String
        val rumSessionState = rumContext?.get("session_state") as? String

        return if (rumApplicationId == null ||
            rumApplicationId == RumContext.NULL_UUID ||
            rumSessionId == null ||
            rumSessionId == RumContext.NULL_UUID ||
            rumSessionState.isNullOrBlank()
        ) {
            rumFeatureDisabled = true
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { RUM_NOT_INITIALIZED_WARNING_MESSAGE }
            )
            null
        } else {
            RumContext(rumApplicationId, rumSessionId, rumSessionState)
        }
    }

    companion object {
        const val RUM_NOT_INITIALIZED_WARNING_MESSAGE = "You are trying to use the WebView " +
            "tracking API but the RUM feature was not properly initialized."
    }
}
