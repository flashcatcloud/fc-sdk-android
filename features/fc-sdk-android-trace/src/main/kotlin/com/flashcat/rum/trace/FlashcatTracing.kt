/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.flashcat.rum.trace

import com.flashcat.rum.Flashcat
import com.flashcat.rum.api.InternalLogger
import com.flashcat.rum.api.SdkCore
import com.flashcat.rum.api.feature.Feature
import com.flashcat.rum.api.feature.FeatureSdkCore
import com.flashcat.rum.trace.api.tracer.DatadogTracerBuilder
import com.flashcat.rum.trace.api.tracer.NoOpDatadogTracerBuilder
import com.flashcat.rum.trace.internal.DatadogSpanWriterWrapper
import com.flashcat.rum.trace.internal.DatadogTracerBuilderAdapter
import com.flashcat.rum.trace.internal.DatadogTracingToolkit
import com.datadog.trace.common.writer.NoOpWriter
import com.datadog.trace.core.CoreTracer

/**
 * Object responsible for providing tracing capabilities in the Flashcat SDK.
 */
@SuppressWarnings("UndocumentedPublicFunction")
object FlashcatTracing {
    /**
     * Creates and returns a new instance of [DatadogTracerBuilder]. This method initializes and configures the
     * tracer builder based on the provided SDK core instance and its setup. If certain required configurations
     * are missing, a no-operation tracer builder is returned instead.
     *
     * @param sdkCore An instance of [SdkCore], which provides configuration and features needed for initializing
     * the tracer builder.
     * @return A configured instance of [DatadogTracerBuilder], or a no-operation implementation if the necessary
     * configurations or features are unavailable.
     */
    @JvmStatic
    fun newTracerBuilder(sdkCore: SdkCore = Datadog.getInstance()): DatadogTracerBuilder = when {
        DatadogTracingToolkit.testBuilderProvider != null -> {
            DatadogTracingToolkit.testBuilderProvider as DatadogTracerBuilder
        }
        sdkCore !is FeatureSdkCore -> {
            NoOpDatadogTracerBuilder()
        }
        else -> {
            val internalLogger = sdkCore.internalLogger
            val tracingFeature = sdkCore.getFeature(Feature.TRACING_FEATURE_NAME)?.unwrap<Feature>()
            val internalCoreWriterProvider = tracingFeature as? InternalCoreWriterProvider
            val writer = (internalCoreWriterProvider?.getCoreTracerWriter() as? DatadogSpanWriterWrapper)?.delegate

            when {
                null == tracingFeature -> internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.USER,
                    { ErrorMessages.TRACING_NOT_ENABLED_ERROR_MESSAGE }
                )

                null == internalCoreWriterProvider -> internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.MAINTAINER,
                    { ErrorMessages.WRITER_PROVIDER_INTERFACE_NOT_IMPLEMENTED_ERROR_MESSAGE }
                )

                null == writer -> internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.USER,
                    {
                        ErrorMessages.buildWrongWrapperMessage(
                            internalCoreWriterProvider.getCoreTracerWriter().javaClass
                        )
                    }
                )

                sdkCore.service.isEmpty() -> internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.USER,
                    { ErrorMessages.DEFAULT_SERVICE_NAME_IS_MISSING_ERROR_MESSAGE }
                )
            }

            DatadogTracerBuilderAdapter(
                sdkCore = sdkCore,
                serviceName = sdkCore.service,
                delegate = CoreTracer.CoreTracerBuilder(sdkCore.internalLogger).writer(writer ?: NoOpWriter())
            )
        }
    }

    internal object ErrorMessages {
        const val TRACING_NOT_ENABLED_ERROR_MESSAGE: String =
            "You're trying to create an DatadogTracer instance, " +
                "but either the SDK was not initialized or the Tracing feature was " +
                "not registered. No tracing data will be sent."

        const val WRITER_PROVIDER_INTERFACE_NOT_IMPLEMENTED_ERROR_MESSAGE: String =
            "The Tracing feature is not implementing the InternalCoreWriterProvider interface." +
                " No tracing data will be sent."

        const val DEFAULT_SERVICE_NAME_IS_MISSING_ERROR_MESSAGE: String =
            "Default service name is missing during DatadogTracerBuilder creation, did you initialize SDK?"

        fun buildWrongWrapperMessage(cls: Class<*>?): String {
            return "You're trying to create an DatadogTracer instance, " +
                "but provided ${cls?.canonicalName} writer wrapper is not supported."
        }
    }
}
