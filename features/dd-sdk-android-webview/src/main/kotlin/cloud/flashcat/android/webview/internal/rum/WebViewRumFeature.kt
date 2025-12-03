/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.webview.internal.rum

import android.content.Context
import cloud.flashcat.android.api.InternalLogger
import cloud.flashcat.android.api.feature.Feature
import cloud.flashcat.android.api.feature.FeatureContextUpdateReceiver
import cloud.flashcat.android.api.feature.FeatureSdkCore
import cloud.flashcat.android.api.feature.StorageBackedFeature
import cloud.flashcat.android.api.net.RequestFactory
import cloud.flashcat.android.api.storage.DataWriter
import cloud.flashcat.android.api.storage.FeatureStorageConfiguration
import cloud.flashcat.android.api.storage.NoOpDataWriter
import cloud.flashcat.android.webview.internal.rum.domain.NativeRumViewsCache
import cloud.flashcat.android.webview.internal.rum.domain.WebViewNativeRumViewsCache
import cloud.flashcat.android.webview.internal.storage.WebViewDataWriter
import cloud.flashcat.android.webview.internal.storage.WebViewEventSerializer
import com.google.gson.JsonObject
import java.util.concurrent.atomic.AtomicBoolean

internal class WebViewRumFeature(
    private val sdkCore: FeatureSdkCore,
    override val requestFactory: RequestFactory,
    internal val nativeRumViewsCache: NativeRumViewsCache = WebViewNativeRumViewsCache()
) : StorageBackedFeature, FeatureContextUpdateReceiver {

    internal var dataWriter: DataWriter<JsonObject> = NoOpDataWriter()

    internal val initialized = AtomicBoolean(false)

    // region Feature

    override val name: String = WEB_RUM_FEATURE_NAME

    override fun onInitialize(appContext: Context) {
        sdkCore.setContextUpdateReceiver(this)
        dataWriter = createDataWriter(sdkCore.internalLogger)
        initialized.set(true)
    }

    override fun onContextUpdate(featureName: String, context: Map<String, Any?>) {
        if (featureName == Feature.RUM_FEATURE_NAME) {
            nativeRumViewsCache.addToCache(context)
        }
    }

    override val storageConfiguration: FeatureStorageConfiguration =
        FeatureStorageConfiguration.DEFAULT

    override fun onStop() {
        sdkCore.removeContextUpdateReceiver(this)
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
        internal const val WEB_RUM_FEATURE_NAME = "web-rum"
    }
}
