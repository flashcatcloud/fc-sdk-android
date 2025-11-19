/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.rum.internal

import com.flashcat.rum.api.context.FlashcatContext
import com.flashcat.rum.api.feature.Feature

internal class FeaturesContextResolver {

    @Suppress("UNCHECKED_CAST")
    fun resolveViewHasReplay(flashcatContext: FlashcatContext, viewId: String): Boolean {
        val sessionReplayContext =
            flashcatContext.featuresContext[Feature.SESSION_REPLAY_FEATURE_NAME] ?: return false
        val sessionReplayMetadata = sessionReplayContext[viewId] as? Map<String, Any?>
        return (sessionReplayMetadata?.get(HAS_REPLAY_KEY) as? Boolean) ?: false
    }

    @Suppress("UNCHECKED_CAST")
    fun resolveViewRecordsCount(flashcatContext: FlashcatContext, viewId: String): Long {
        val sessionReplayContext =
            flashcatContext.featuresContext[Feature.SESSION_REPLAY_FEATURE_NAME] ?: return 0L
        val sessionReplayMetadata = sessionReplayContext[viewId] as? Map<String, Any?>
        return (sessionReplayMetadata?.get(VIEW_RECORDS_COUNT_KEY) as? Long) ?: 0L
    }

    companion object {
        internal const val HAS_REPLAY_KEY = "has_replay"
        internal const val VIEW_RECORDS_COUNT_KEY = "records_count"
    }
}
