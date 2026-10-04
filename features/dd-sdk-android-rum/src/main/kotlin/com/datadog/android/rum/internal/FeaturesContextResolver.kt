/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature

internal class FeaturesContextResolver {

    @Suppress("UNCHECKED_CAST")
    fun resolveViewHasReplay(datadogContext: DatadogContext, viewId: String): Boolean {
        val sessionReplayContext =
            datadogContext.featuresContext[Feature.SESSION_REPLAY_FEATURE_NAME] ?: return false
        val sessionReplayMetadata = sessionReplayContext[viewId] as? Map<String, Any?>
        return (sessionReplayMetadata?.get(HAS_REPLAY_KEY) as? Boolean) ?: false
    }

    @Suppress("UNCHECKED_CAST")
    fun resolveViewRecordsCount(datadogContext: DatadogContext, viewId: String): Long {
        val sessionReplayContext =
            datadogContext.featuresContext[Feature.SESSION_REPLAY_FEATURE_NAME] ?: return 0L
        val sessionReplayMetadata = sessionReplayContext[viewId] as? Map<String, Any?>
        return (sessionReplayMetadata?.get(VIEW_RECORDS_COUNT_KEY) as? Long) ?: 0L
    }

    /**
     * FLASHCAT FORK - whether the replay of this session is kept only because it reports an error,
     * or null when Session Replay is not enabled.
     */
    fun resolveSampledForErrorReplay(datadogContext: DatadogContext, sessionId: String): Boolean? {
        val sessionReplayContext =
            datadogContext.featuresContext[Feature.SESSION_REPLAY_FEATURE_NAME] ?: return null
        return sessionReplayContext[REPLAY_ON_ERROR_SESSION_KEY] == sessionId
    }

    /**
     * FLASHCAT FORK - whether this session is sampled for replay, or null when Session Replay is not
     * enabled. A replay still withheld counts when the session's events are withheld too: it is
     * released along with them, so if these events ever reach the intake, so does the replay.
     */
    fun resolveSampledForReplay(datadogContext: DatadogContext, sampledForError: Boolean): Boolean? {
        val sessionReplayContext =
            datadogContext.featuresContext[Feature.SESSION_REPLAY_FEATURE_NAME] ?: return null
        val isRecording = sessionReplayContext[REPLAY_ENABLED_KEY] as? Boolean ?: false
        val isWithheld = sessionReplayContext[REPLAY_WITHHELD_KEY] as? Boolean ?: false
        return isRecording && (!isWithheld || sampledForError)
    }

    companion object {
        internal const val HAS_REPLAY_KEY = "has_replay"
        internal const val VIEW_RECORDS_COUNT_KEY = "records_count"
        internal const val REPLAY_ENABLED_KEY = "session_replay_is_enabled"
        internal const val REPLAY_ON_ERROR_SESSION_KEY = "session_replay_on_error_session_id"
        internal const val REPLAY_WITHHELD_KEY = "session_replay_withheld"
    }
}
