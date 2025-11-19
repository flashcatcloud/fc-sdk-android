/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.trace.internal.data

import androidx.annotation.WorkerThread
import com.flashcat.rum.api.InternalLogger
import com.flashcat.rum.api.context.FlashcatContext
import com.flashcat.rum.api.feature.Feature
import com.flashcat.rum.api.feature.FeatureSdkCore
import com.flashcat.rum.api.storage.EventBatchWriter
import com.flashcat.rum.api.storage.EventType
import com.flashcat.rum.api.storage.RawBatchEvent
import com.flashcat.rum.event.EventMapper
import com.flashcat.rum.event.NoOpEventMapper
import com.flashcat.rum.trace.internal.RumContextPropagator
import com.flashcat.rum.trace.internal.RumContextPropagator.Companion.extractRumContext
import com.flashcat.rum.trace.internal.domain.event.ContextAwareMapper
import com.flashcat.rum.trace.internal.storage.ContextAwareSerializer
import com.flashcat.rum.trace.model.SpanEvent
import com.datadog.trace.api.sampling.PrioritySampling
import com.datadog.trace.common.writer.Writer
import com.datadog.trace.core.DDSpan
import java.util.Locale

internal class CoreTraceWriter(
    private val sdkCore: FeatureSdkCore,
    internal val ddSpanToSpanEventMapper: ContextAwareMapper<DDSpan, SpanEvent>,
    internal val eventMapper: EventMapper<SpanEvent> = NoOpEventMapper(),
    private val serializer: ContextAwareSerializer<SpanEvent>,
    private val internalLogger: InternalLogger,
    private val rumContextPropagator: RumContextPropagator = RumContextPropagator { sdkCore }
) : Writer {

    // region Writer
    override fun start() {
        // NO - OP
    }

    override fun write(trace: List<DDSpan>?) {
        if (trace == null) return
        sdkCore.getFeature(Feature.TRACING_FEATURE_NAME)
            ?.withWriteContext { flashcatContext, writeScope ->
                val writeSpans = trace
                    .filter { it.getTraceSamplingPriority() !in DROP_SAMPLING_PRIORITIES }
                    .map { it.extractRumContext(rumContextPropagator) }
                // TODO RUM-4092 Add the capability in the serializer to handle multiple spans in one payload
                writeScope {
                    writeSpans
                        .forEach { span ->
                            @Suppress("ThreadSafety") // called in the worker context
                            writeSpan(flashcatContext, it, span)
                        }
                }
            }
    }

    override fun flush(): Boolean {
        // NO - OP
        return true
    }

    override fun incrementDropCounts(p0: Int) {
        // NO - OP
    }

    override fun close() {
        // NO - OP
    }
    // endregion

    @WorkerThread
    private fun writeSpan(
        flashcatContext: FlashcatContext,
        writer: EventBatchWriter,
        span: DDSpan
    ) {
        val spanEvent = ddSpanToSpanEventMapper.map(flashcatContext, span)
        val mapped = eventMapper.map(spanEvent) ?: return
        try {
            val serialized = serializer
                .serialize(flashcatContext, mapped)
                ?.toByteArray(Charsets.UTF_8) ?: return
            synchronized(this) {
                writer.write(RawBatchEvent(data = serialized), batchMetadata = null, eventType = EventType.DEFAULT)
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
                { ERROR_SERIALIZING.format(Locale.US, mapped.javaClass.simpleName) },
                e
            )
        }
    }

    companion object {
        internal const val ERROR_SERIALIZING = "Error serializing %s model"
        internal val DROP_SAMPLING_PRIORITIES = setOf(
            PrioritySampling.SAMPLER_DROP.toInt(),
            PrioritySampling.USER_DROP.toInt()
        )
    }
}
