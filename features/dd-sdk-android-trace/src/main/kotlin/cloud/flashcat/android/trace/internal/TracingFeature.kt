/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.trace.internal

import android.content.Context
import cloud.flashcat.android.api.feature.Feature
import cloud.flashcat.android.api.feature.FeatureSdkCore
import cloud.flashcat.android.api.feature.StorageBackedFeature
import cloud.flashcat.android.api.net.RequestFactory
import cloud.flashcat.android.api.storage.FeatureStorageConfiguration
import cloud.flashcat.android.trace.InternalCoreWriterProvider
import cloud.flashcat.android.trace.event.SpanEventMapper
import cloud.flashcat.android.trace.internal.data.CoreTraceWriter
import cloud.flashcat.android.trace.internal.domain.event.CoreTracerSpanToSpanEventMapper
import cloud.flashcat.android.trace.internal.domain.event.SpanEventMapperWrapper
import cloud.flashcat.android.trace.internal.domain.event.SpanEventSerializer
import cloud.flashcat.android.trace.internal.net.TracesRequestFactory
import cloud.flashcat.trace.common.writer.NoOpWriter
import cloud.flashcat.trace.common.writer.Writer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tracing feature class, which needs to be registered with Datadog SDK instance.
 */
internal class TracingFeature(
    private val sdkCore: FeatureSdkCore,
    customEndpointUrl: String?,
    internal val spanEventMapper: SpanEventMapper,
    internal val networkInfoEnabled: Boolean
) : InternalCoreWriterProvider, StorageBackedFeature {

    internal var coreTracerDataWriter: Writer = NoOpWriter()
    internal val initialized = AtomicBoolean(false)

    // region Feature

    override val name: String = Feature.TRACING_FEATURE_NAME

    override fun onInitialize(appContext: Context) {
        coreTracerDataWriter = createDataWriter(sdkCore)
        initialized.set(true)
    }

    override val requestFactory: RequestFactory by lazy {
        TracesRequestFactory(
            customEndpointUrl,
            sdkCore.internalLogger
        )
    }

    override val storageConfiguration: FeatureStorageConfiguration =
        FeatureStorageConfiguration.DEFAULT

    override fun onStop() {
        initialized.set(false)
    }

    // endregion

    // region InternalCoreWriterProvider

    override fun getCoreTracerWriter() = DatadogSpanWriterWrapper(coreTracerDataWriter)

    // endregion

    private fun createDataWriter(sdkCore: FeatureSdkCore): Writer {
        val internalLogger = sdkCore.internalLogger
        return CoreTraceWriter(
            sdkCore,
            ddSpanToSpanEventMapper = CoreTracerSpanToSpanEventMapper(networkInfoEnabled),
            eventMapper = SpanEventMapperWrapper(spanEventMapper, internalLogger),
            serializer = SpanEventSerializer(internalLogger),
            internalLogger = internalLogger
        )
    }
}
