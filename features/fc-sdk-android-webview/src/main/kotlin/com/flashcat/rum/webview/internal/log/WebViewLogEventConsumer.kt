/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.webview.internal.log

import com.flashcat.rum.api.InternalLogger
import com.flashcat.rum.api.context.FlashcatContext
import com.flashcat.rum.api.feature.Feature
import com.flashcat.rum.api.feature.FeatureSdkCore
import com.flashcat.rum.api.storage.DataWriter
import com.flashcat.rum.api.storage.EventType
import com.flashcat.rum.core.sampling.RateBasedSampler
import com.flashcat.rum.core.sampling.Sampler
import com.flashcat.rum.log.LogAttributes
import com.flashcat.rum.webview.internal.WebViewEventConsumer
import com.flashcat.rum.webview.internal.rum.WebViewRumEventContextProvider
import com.flashcat.rum.webview.internal.rum.domain.RumContext
import com.google.gson.JsonObject

internal class WebViewLogEventConsumer(
    private val sdkCore: FeatureSdkCore,
    internal val userLogsWriter: DataWriter<JsonObject>,
    private val rumContextProvider: WebViewRumEventContextProvider,
    sampleRate: Float
) : WebViewEventConsumer<Pair<JsonObject, String>> {

    private val sampler: Sampler<Unit> = RateBasedSampler(sampleRate)

    override fun consume(event: Pair<JsonObject, String>) {
        if (event.second == USER_LOG_EVENT_TYPE) {
            if (sampler.sample(Unit)) {
                sdkCore.getFeature(WebViewLogsFeature.WEB_LOGS_FEATURE_NAME)
                    ?.withWriteContext(
                        withFeatureContexts = setOf(Feature.RUM_FEATURE_NAME)
                    ) { flashcatContext, writeScope ->
                        val rumContext = rumContextProvider.getRumContext(flashcatContext)
                        writeScope {
                            val mappedEvent = map(event.first, flashcatContext, rumContext)
                            userLogsWriter.write(it, mappedEvent, EventType.DEFAULT)
                        }
                    }
            }
        }
    }

    private fun map(
        event: JsonObject,
        flashcatContext: FlashcatContext,
        rumContext: RumContext?
    ): JsonObject {
        addDdTags(event, flashcatContext)
        correctDate(event, flashcatContext)
        if (rumContext != null) {
            event.addProperty(LogAttributes.RUM_APPLICATION_ID, rumContext.applicationId)
            event.addProperty(LogAttributes.RUM_SESSION_ID, rumContext.sessionId)
        }
        return event
    }

    private fun correctDate(event: JsonObject, flashcatContext: FlashcatContext) {
        try {
            event.get(DATE_KEY_NAME)?.asLong?.let {
                event.addProperty(
                    DATE_KEY_NAME,
                    it + flashcatContext.time.serverTimeOffsetMs
                )
            }
        } catch (e: ClassCastException) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { JSON_PARSING_ERROR_MESSAGE },
                e
            )
        } catch (e: IllegalStateException) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { JSON_PARSING_ERROR_MESSAGE },
                e
            )
        } catch (e: NumberFormatException) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { JSON_PARSING_ERROR_MESSAGE },
                e
            )
        } catch (e: UnsupportedOperationException) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { JSON_PARSING_ERROR_MESSAGE },
                e
            )
        }
    }

    private fun addDdTags(event: JsonObject, flashcatContext: FlashcatContext) {
        val sdkDdTags = mapOf(
            LogAttributes.APPLICATION_VERSION to flashcatContext.version,
            LogAttributes.ENV to flashcatContext.env,
            LogAttributes.VARIANT to flashcatContext.variant,
            LogAttributes.SERVICE to flashcatContext.service
        )
        val eventDdTags = try {
            event.get(DDTAGS_KEY_NAME)?.asString?.let {
                it.split(DDTAGS_SEPARATOR)
                    .mapNotNull { tag ->
                        @Suppress("UnsafeThirdPartyFunctionCall") // safe indexOf invocation
                        val splitIndex = tag.indexOf(":")
                        if (splitIndex == -1 || splitIndex == tag.lastIndex) {
                            null
                        } else {
                            @Suppress("UnsafeThirdPartyFunctionCall") // safe substring invocations
                            tag.substring(0, splitIndex) to tag.substring(splitIndex + 1)
                        }
                    }
                    .associate { it }
            }.orEmpty()
        } catch (e: ClassCastException) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { JSON_PARSING_ERROR_MESSAGE },
                e
            )
            emptyMap<String, String>()
        } catch (e: IllegalStateException) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { JSON_PARSING_ERROR_MESSAGE },
                e
            )
            emptyMap<String, String>()
        } catch (e: UnsupportedOperationException) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { JSON_PARSING_ERROR_MESSAGE },
                e
            )
            emptyMap<String, String>()
        }
        event.addProperty(
            DDTAGS_KEY_NAME,
            (eventDdTags + sdkDdTags)
                .map { "${it.key}:${it.value}" }
                .joinToString(DDTAGS_SEPARATOR)
        )
    }

    companion object {
        const val DDTAGS_SEPARATOR = ","
        const val DDTAGS_KEY_NAME = "ddtags"
        const val DATE_KEY_NAME = "date"
        const val USER_LOG_EVENT_TYPE = "log"
        const val INTERNAL_LOG_EVENT_TYPE = "internal_log"
        const val JSON_PARSING_ERROR_MESSAGE = "The bundled web log event could not be deserialized"
        val LOG_EVENT_TYPES = setOf(USER_LOG_EVENT_TYPE)
        internal const val DEFAULT_SAMPLE_RATE = 100f
    }
}
