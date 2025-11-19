/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sessionreplay

import androidx.annotation.VisibleForTesting
import com.flashcat.rum.Flashcat
import com.flashcat.rum.api.InternalLogger
import com.flashcat.rum.api.SdkCore
import com.flashcat.rum.api.feature.Feature
import com.flashcat.rum.api.feature.FeatureSdkCore
import com.flashcat.rum.sessionreplay.internal.SessionReplayFeature
import com.flashcat.rum.sessionreplay.internal.TouchPrivacyManager
import java.lang.ref.WeakReference

/**
 * An entry point to Datadog Session Replay feature.
 */
object SessionReplay {

    @VisibleForTesting internal var currentRegisteredCore: WeakReference<SdkCore>? = null

    internal const val IS_ALREADY_REGISTERED_WARNING =
        "Session Replay is already enabled and does not support multiple instances. " +
            "The existing instance will continue to be used."

    /**
     * Enables a SessionReplay feature based on the configuration provided.
     * It is recommended to invoke this function as early as possible in the app's lifecycle,
     * ideally within the `Application#onCreate` callback, to ensure proper initialization.
     *
     * @param sessionReplayConfiguration Configuration to use for the feature.
     * @param sdkCore SDK instance to register feature in. If not provided, default SDK instance
     * will be used.
     */
    @JvmOverloads
    @JvmStatic
    fun enable(
        sessionReplayConfiguration: SessionReplayConfiguration,
        sdkCore: SdkCore = Datadog.getInstance()
    ) {
        val featureSdkCore = sdkCore as FeatureSdkCore
        sessionReplayConfiguration.systemRequirementsConfiguration
            .runIfRequirementsMet(featureSdkCore.internalLogger) {
                val touchPrivacyManager = TouchPrivacyManager(sessionReplayConfiguration.touchPrivacy)
                val sessionReplayFeature = SessionReplayFeature(
                    sdkCore = featureSdkCore,
                    customEndpointUrl = sessionReplayConfiguration.customEndpointUrl,
                    privacy = sessionReplayConfiguration.privacy,
                    imagePrivacy = sessionReplayConfiguration.imagePrivacy,
                    touchPrivacy = sessionReplayConfiguration.touchPrivacy,
                    touchPrivacyManager = touchPrivacyManager,
                    textAndInputPrivacy = sessionReplayConfiguration.textAndInputPrivacy,
                    customMappers = sessionReplayConfiguration.customMappers,
                    customOptionSelectorDetectors = sessionReplayConfiguration.customOptionSelectorDetectors,
                    customDrawableMappers = sessionReplayConfiguration.customDrawableMappers,
                    sampleRate = sessionReplayConfiguration.sampleRate,
                    startRecordingImmediately = sessionReplayConfiguration.startRecordingImmediately,
                    dynamicOptimizationEnabled = sessionReplayConfiguration.dynamicOptimizationEnabled,
                    internalCallback = sessionReplayConfiguration.internalCallback
                )

                if (isAlreadyRegistered()) {
                    logAlreadyRegisteredWarning(sdkCore.internalLogger)
                } else {
                    currentRegisteredCore = WeakReference(sdkCore)
                    sdkCore.registerFeature(sessionReplayFeature)
                }
            }
    }

    /**
     * Start recording session replay data.
     * @param sdkCore SDK instance to get the feature from. If not provided, default SDK instance
     * will be used.
     */
    fun startRecording(
        sdkCore: SdkCore = Datadog.getInstance()
    ) {
        val sessionReplayFeature = (sdkCore as? FeatureSdkCore)
            ?.getFeature(Feature.SESSION_REPLAY_FEATURE_NAME)?.let {
                it.unwrap() as? SessionReplayFeature
            }

        sessionReplayFeature?.manuallyStartRecording()
    }

    /**
     * Stop recording session replay data.
     * @param sdkCore SDK instance to get the feature from. If not provided, default SDK instance
     * will be used.
     */
    fun stopRecording(
        sdkCore: SdkCore = Datadog.getInstance()
    ) {
        val sessionReplayFeature = (sdkCore as? FeatureSdkCore)
            ?.getFeature(Feature.SESSION_REPLAY_FEATURE_NAME)?.let {
                it.unwrap() as? SessionReplayFeature
            }

        sessionReplayFeature?.manuallyStopRecording()
    }

    private fun isAlreadyRegistered() =
        currentRegisteredCore?.get()?.isCoreActive() == true

    private fun logAlreadyRegisteredWarning(internalLogger: InternalLogger) {
        internalLogger.log(
            level = InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.MAINTAINER),
            messageBuilder = { IS_ALREADY_REGISTERED_WARNING }
        )

        internalLogger.log(
            level = InternalLogger.Level.DEBUG,
            targets = listOf(InternalLogger.Target.TELEMETRY),
            messageBuilder = { IS_ALREADY_REGISTERED_WARNING }
        )
    }
}
