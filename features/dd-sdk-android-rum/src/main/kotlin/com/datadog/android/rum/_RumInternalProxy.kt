/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import android.app.Activity
import android.content.Intent
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.event.EventMapper
import com.datadog.android.lint.InternalApi
import com.datadog.android.rum.RumConfiguration.Builder
import com.datadog.android.rum.internal.instrumentation.insights.InsightsCollector
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.internal.startup.RumStartupScenario
import com.datadog.android.rum.internal.startup.RumTTIDInfo
import com.datadog.android.rum.tracking.ActionTrackingStrategy
import com.datadog.android.telemetry.model.TelemetryConfigurationEvent

/**
 * This class exposes internal methods that are used by other Datadog modules and cross platform
 * frameworks. It is not meant for public use.
 *
 * DO NOT USE this class or its methods if you are not working on the internals of the Datadog SDK
 * or one of the cross platform frameworks.
 *
 * Methods, members, and functionality of this class  are subject to change without notice, as they
 * are not considered part of the public interface of the Datadog SDK.
 */
@InternalApi
@Suppress(
    "UndocumentedPublicClass",
    "UndocumentedPublicFunction",
    "UndocumentedPublicProperty",
    "ClassName",
    "ClassNaming",
    "VariableNaming"
)
class _RumInternalProxy internal constructor(
    private val rumMonitor: AdvancedRumMonitor,
    private val sdkCore: InternalSdkCore
) {
    @Volatile private var handledSyntheticsAttribute = false

    fun addLongTask(durationNs: Long, target: String) {
        rumMonitor.addLongTask(durationNs, target)
    }

    fun updatePerformanceMetric(metric: RumPerformanceMetric, value: Double) {
        rumMonitor.updatePerformanceMetric(metric, value)
    }

    fun updateExternalRefreshRate(frameTimeSeconds: Double) {
        rumMonitor.updateExternalRefreshRate(frameTimeSeconds)
    }

    /**
     * Report an app-launch (TTID) vital measured by a host framework (e.g. Flutter), whose late
     * SDK initialization means the native RumAppStartupDetector never sees the first Activity
     * and so never fires. Emits the same VitalAppLaunchEvent(metric=TTID) the native detector
     * would.
     *
     * [uiCreateTimeNs] is a [System.nanoTime] reading taken when the host framework's UI was
     * created - for Flutter, when the first Activity is attached. It plays the role
     * first-Activity-onCreate plays in RumAppStartupDetectorImpl, so a launch is classified
     * cold or warm here exactly as it would be natively. Pass 0 when the host cannot supply
     * it; the launch is then reported as cold, which is what a host that only knows about
     * process start can honestly claim.
     *
     * [frameEndOffsetNs] is how long before this call the launch frame actually finished
     * displaying. TTID ends at that frame, not at the moment the host got around to calling
     * us, so without it the host's own callback scheduling and any cross-language call
     * overhead would be counted as launch time. Pass 0 when the host cannot measure it.
     *
     * The process start time comes from the core rather than from
     * android.os.Process.getStartElapsedRealtime directly, because the core already discards
     * the buggy readings that API occasionally returns - see DefaultAppStartTimeProvider.
     */
    fun notifyAppLaunch(uiCreateTimeNs: Long, frameEndOffsetNs: Long) {
        val processStartTimeNs = sdkCore.appStartTimeNs
        val gapNs = if (uiCreateTimeNs > 0L) uiCreateTimeNs - processStartTimeNs else 0L

        // A long gap between process start and the first UI being created means the process was
        // already alive when the user opened the app, so the launch is warm and must be measured
        // from the UI creation instead of from process start.
        val isCold = gapNs <= START_GAP_THRESHOLD_NS
        val initialTimeNs = if (isCold) processStartTimeNs else uiCreateTimeNs

        val nowNs = System.nanoTime()
        val frameEndNs = nowNs - frameEndOffsetNs.coerceAtLeast(0L)
        val durationNs = frameEndNs - initialTimeNs
        if (durationNs <= 0L) return

        // Anchored on now rather than on durationNs: the launch started that long ago in wall
        // clock terms, whereas durationNs deliberately stops short at the launch frame.
        val elapsedSinceStartNs = nowNs - initialTimeNs
        val initialTime = Time(
            timestamp = System.currentTimeMillis() -
                TimeUnit.NANOSECONDS.toMillis(elapsedSinceStartNs),
            nanoTime = initialTimeNs
        )
        val scenario = if (isCold) {
            RumStartupScenario.Cold(
                hasSavedInstanceStateBundle = false,
                activity = WeakReference(null),
                appStartActivityOnCreateGapNs = gapNs,
                initialTime = initialTime
            )
        } else {
            RumStartupScenario.WarmFirstActivity(
                hasSavedInstanceStateBundle = false,
                activity = WeakReference(null),
                appStartActivityOnCreateGapNs = gapNs,
                initialTime = initialTime
            )
        }
        // The native detector always emits the app-start event before the TTID one, and the
        // session scope depends on that ordering: it numbers the launch within the session and
        // records the scenario that a later TTFD is measured against. Emitting only the TTID
        // event would leave the launch numbered -1 and the TTFD state unset.
        rumMonitor.sendAppStartEvent(scenario)
        rumMonitor.sendTTIDEvent(RumTTIDInfo(scenario, durationNs))
    }

    fun setInternalViewAttribute(key: String, value: Any?) {
        rumMonitor.setInternalViewAttribute(key, value)
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun setSyntheticsAttribute(testId: String?, resultId: String?) {
        if (this.handledSyntheticsAttribute) {
            return
        }

        this.handledSyntheticsAttribute = true
        if (testId.isNullOrBlank() || resultId.isNullOrBlank()) {
            return
        }

        rumMonitor.setSyntheticsAttribute(testId, resultId)
    }

    /**
     * Enables the tracking of JankStats for the given activity. This should only be necessary for the
     * initial activity of an application if Datadog is initialized after that activity is created.
     * @param activity the activity to track
     */
    fun enableJankStatsTracking(activity: Activity) {
        rumMonitor.enableJankStatsTracking(activity)
    }

    fun setSyntheticsAttributeFromIntent(intent: Intent) {
        @Suppress("TooGenericExceptionCaught")
        val extras = try { intent.extras } catch (_: Exception) { null }
        val testId = extras?.getString("_dd.synthetics.test_id")
        val resultId = extras?.getString("_dd.synthetics.result_id")
        this.setSyntheticsAttribute(testId, resultId)
    }

    companion object {

        @Suppress("FunctionMaxLength")
        fun setTelemetryConfigurationEventMapper(
            builder: Builder,
            eventMapper: EventMapper<TelemetryConfigurationEvent>
        ): Builder {
            return builder.setTelemetryConfigurationEventMapper(eventMapper)
        }

        @Suppress("unused")
        fun setAdditionalConfiguration(
            builder: Builder,
            additionalConfig: Map<String, Any>
        ): Builder {
            return builder.setAdditionalConfiguration(additionalConfig)
        }

        fun setComposeActionTrackingStrategy(
            builder: Builder,
            composeActionTrackingStrategy: ActionTrackingStrategy
        ): Builder {
            return builder.setComposeActionTrackingStrategy(composeActionTrackingStrategy)
        }

        fun setRumSessionTypeOverride(
            builder: Builder,
            rumSessionTypeOverride: RumSessionType
        ): Builder {
            return builder.setRumSessionTypeOverride(rumSessionTypeOverride)
        }

        fun setDisableJankStats(
            builder: Builder,
            disable: Boolean
        ): Builder {
            return builder.setDisableJankStats(disable)
        }

        fun setInsightsCollector(
            builder: Builder,
            insightsCollector: InsightsCollector
        ): Builder {
            return builder.setInsightsCollector(insightsCollector)
        }
    }
}

// Kept in sync with RumAppStartupDetectorImpl.START_GAP_THRESHOLD_NS so that a launch reported
// by a cross-platform host is classified the same way the native detector would classify it.
private val START_GAP_THRESHOLD_NS = 10.seconds.inWholeNanoseconds
