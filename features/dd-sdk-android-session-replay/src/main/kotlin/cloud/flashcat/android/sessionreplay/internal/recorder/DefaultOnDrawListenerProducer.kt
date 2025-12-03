/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.sessionreplay.internal.recorder

import android.view.View
import android.view.ViewTreeObserver
import cloud.flashcat.android.api.feature.FeatureSdkCore
import cloud.flashcat.android.core.metrics.MethodCallSamplingRate
import cloud.flashcat.android.sessionreplay.ImagePrivacy
import cloud.flashcat.android.sessionreplay.TextAndInputPrivacy
import cloud.flashcat.android.sessionreplay.internal.TouchPrivacyManager
import cloud.flashcat.android.sessionreplay.internal.async.RecordedDataQueueHandler
import cloud.flashcat.android.sessionreplay.internal.recorder.listener.WindowsOnDrawListener

internal class DefaultOnDrawListenerProducer(
    private val snapshotProducer: SnapshotProducer,
    private val recordedDataQueueHandler: RecordedDataQueueHandler,
    private val sdkCore: FeatureSdkCore,
    private val dynamicOptimizationEnabled: Boolean
) : OnDrawListenerProducer {

    override fun create(
        decorViews: List<View>,
        textAndInputPrivacy: TextAndInputPrivacy,
        imagePrivacy: ImagePrivacy,
        touchPrivacyManager: TouchPrivacyManager
    ): ViewTreeObserver.OnDrawListener {
        return WindowsOnDrawListener(
            zOrderedDecorViews = decorViews,
            recordedDataQueueHandler = recordedDataQueueHandler,
            snapshotProducer = snapshotProducer,
            textAndInputPrivacy = textAndInputPrivacy,
            imagePrivacy = imagePrivacy,
            sdkCore = sdkCore,
            methodCallSamplingRate = MethodCallSamplingRate.LOW.rate,
            dynamicOptimizationEnabled = dynamicOptimizationEnabled,
            touchPrivacyManager = touchPrivacyManager
        )
    }
}
