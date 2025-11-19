/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.webview.internal.replay

import android.content.Context
import com.flashcat.rum.api.InternalLogger
import com.flashcat.rum.api.feature.FeatureSdkCore
import com.flashcat.rum.api.feature.StorageBackedFeature
import com.flashcat.rum.api.net.RequestFactory
import com.flashcat.rum.api.storage.DataWriter
import com.flashcat.rum.api.storage.FeatureStorageConfiguration
import com.flashcat.rum.api.storage.NoOpDataWriter
import com.flashcat.rum.webview.internal.storage.WebViewDataWriter
import com.flashcat.rum.webview.internal.storage.WebViewEventSerializer
import com.google.gson.JsonObject
import java.util.concurrent.atomic.AtomicBoolean

internal class WebViewReplayFeature(
    private val sdkCore: FeatureSdkCore,
    override val requestFactory: RequestFactory
) : StorageBackedFeature {

    internal var dataWriter: DataWriter<JsonObject> = NoOpDataWriter()
    internal val initialized = AtomicBoolean(false)

    // region Feature

    override val name: String = WEB_REPLAY_FEATURE_NAME

    override fun onInitialize(appContext: Context) {
        dataWriter = createDataWriter(sdkCore.internalLogger)
        initialized.set(true)
    }

    override val storageConfiguration: FeatureStorageConfiguration =
        STORAGE_CONFIGURATION

    override fun onStop() {
        dataWriter = NoOpDataWriter()
        initialized.set(false)
    }

    // endregion

    private fun createDataWriter(internalLogger: InternalLogger): DataWriter<JsonObject> {
        return WebViewDataWriter(
            serializer = WebViewEventSerializer(),
            internalLogger = internalLogger
        )
    }

    companion object {
        internal const val WEB_REPLAY_FEATURE_NAME = "web-replay"

        /**
         * Storage configuration with the following parameters:
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
