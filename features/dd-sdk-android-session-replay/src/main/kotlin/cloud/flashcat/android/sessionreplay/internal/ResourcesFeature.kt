/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.sessionreplay.internal

import android.content.Context
import cloud.flashcat.android.api.feature.Feature
import cloud.flashcat.android.api.feature.FeatureSdkCore
import cloud.flashcat.android.api.feature.StorageBackedFeature
import cloud.flashcat.android.api.net.RequestFactory
import cloud.flashcat.android.api.storage.FeatureStorageConfiguration
import cloud.flashcat.android.sessionreplay.internal.net.ResourcesRequestFactory
import cloud.flashcat.android.sessionreplay.internal.storage.NoOpResourcesWriter
import cloud.flashcat.android.sessionreplay.internal.storage.ResourcesWriter
import cloud.flashcat.android.sessionreplay.internal.storage.SessionReplayResourcesWriter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Session Replay Resources feature class, which needs to be registered with Session Replay Feature.
 */
internal class ResourcesFeature(
    private val sdkCore: FeatureSdkCore,
    customEndpointUrl: String?
) : StorageBackedFeature {

    internal var dataWriter: ResourcesWriter = NoOpResourcesWriter()
    internal val initialized = AtomicBoolean(false)

    // region Feature

    override val name: String = Feature.SESSION_REPLAY_RESOURCES_FEATURE_NAME

    override val requestFactory: RequestFactory = ResourcesRequestFactory(
        customEndpointUrl = customEndpointUrl,
        internalLogger = sdkCore.internalLogger
    )

    override fun onInitialize(appContext: Context) {
        dataWriter = SessionReplayResourcesWriter(sdkCore)
        initialized.set(true)
    }

    override val storageConfiguration: FeatureStorageConfiguration =
        STORAGE_CONFIGURATION

    override fun onStop() {
        dataWriter = NoOpResourcesWriter()
        initialized.set(false)
    }

    // endregion

    internal companion object {

        /**
         * Session Replay Resources storage configuration with the following parameters:
         * max item size = 10 MB,
         * max items per batch = 500,
         * max batch size = 10 MB, SR intake batch limit is 10MB
         * old batch threshold = 18 hours.
         */
        internal val STORAGE_CONFIGURATION: FeatureStorageConfiguration =
            FeatureStorageConfiguration.DEFAULT.copy(
                maxItemSize = 10 * 1024 * 1024,
                maxBatchSize = 10 * 1024 * 1024
            )
    }
}
