/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.rum.internal.metric

import cloud.flashcat.android.rum.internal.domain.scope.RumSessionScope
import cloud.flashcat.android.rum.model.ViewEvent
import cloud.flashcat.tools.annotation.NoOpImplementation

/**
 * Interface to dispatch the session metric.
 */
@NoOpImplementation
internal interface SessionMetricDispatcher {

    /**
     * Starts a session metric with given session id and start reason of this session.
     */
    fun startMetric(
        sessionId: String,
        startReason: RumSessionScope.StartReason,
        ntpOffsetAtStartMs: Long,
        backgroundEventTracking: Boolean
    )

    /**
     * Ends the session metric with given session id.
     */
    fun endMetric(sessionId: String, ntpOffsetAtEndMs: Long)

    /**
     * Called when the session is stopped.
     */
    fun onSessionStopped(sessionId: String)

    /**
     * Called when a view is tracked by this session metric.
     */
    fun onViewTracked(sessionId: String, viewEvent: ViewEvent)

    /**
     * Called when a sdk error is tracked by this session metric.
     */
    fun onSdkErrorTracked(sessionId: String, errorKind: String?)

    /**
     * Called when a missed event is tracked by this session metric.
     */
    fun onMissedEventTracked(sessionId: String, missedEventType: SessionEndedMetric.MissedEventType)

    /**
     * Called when skipped frame is tracked by this session metric.
     */
    fun onSessionReplaySkippedFrameTracked(sessionId: String)
}
