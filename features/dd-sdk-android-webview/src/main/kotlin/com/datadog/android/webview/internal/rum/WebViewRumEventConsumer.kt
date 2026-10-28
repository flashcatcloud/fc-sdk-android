/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.rum

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.webview.internal.WebViewEventConsumer
import com.datadog.android.webview.internal.replay.WebViewReplayEventConsumer
import com.datadog.android.webview.internal.rum.domain.RumContext
import com.google.gson.JsonObject

internal class WebViewRumEventConsumer(
    private val sdkCore: FeatureSdkCore,
    internal val dataWriter: DataWriter<JsonObject>,
    internal val offsetProvider: TimestampOffsetProvider,
    private val webViewRumEventMapper: WebViewRumEventMapper,
    private val contextProvider: WebViewRumEventContextProvider =
        WebViewRumEventContextProvider(sdkCore.internalLogger)

) : WebViewEventConsumer<JsonObject> {

    @WorkerThread
    override fun consume(event: JsonObject) {
        // make sure we send a noop event to the RumSessionScope to refresh the session if needed
        sdkCore.getFeature(Feature.RUM_FEATURE_NAME)?.sendEvent(
            mapOf(
                "type" to "web_view_ingested_notification"
            )
        )
        sdkCore.getFeature(WebViewRumFeature.WEB_RUM_FEATURE_NAME)
            ?.withWriteContext(
                withFeatureContexts = setOf(Feature.RUM_FEATURE_NAME, Feature.SESSION_REPLAY_FEATURE_NAME)
            ) { datadogContext, writeScope ->
                val rumContext = contextProvider.getRumContext(datadogContext)
                if (rumContext != null && rumContext.sessionState == WITHHELD_SESSION_STATE) {
                    handOverWithheld(event, datadogContext, rumContext)
                } else if (rumContext != null && rumContext.sessionState == "TRACKED") {
                    val sessionReplayFeatureContext = datadogContext.featuresContext[
                        Feature.SESSION_REPLAY_FEATURE_NAME
                    ]
                    // FLASHCAT FORK - a replay still held on error is not one these events may claim.
                    val sessionReplayEnabled = (
                        sessionReplayFeatureContext?.get(
                            WebViewReplayEventConsumer.SESSION_REPLAY_ENABLED_KEY
                        ) as? Boolean ?: false
                        ) && sessionReplayFeatureContext?.get(
                        WebViewReplayEventConsumer.SESSION_REPLAY_WITHHELD_KEY
                    ) != true
                    writeScope {
                        val mappedEvent = map(event, datadogContext, rumContext, sessionReplayEnabled)
                        dataWriter.write(it, mappedEvent, EventType.DEFAULT)
                        if (isSessionError(mappedEvent)) {
                            sdkCore.getFeature(Feature.RUM_FEATURE_NAME)?.sendEvent(
                                mapOf(
                                    "type" to WEB_VIEW_ERROR_MESSAGE_TYPE,
                                    WEB_VIEW_SESSION_ID_KEY to rumContext.sessionId
                                )
                            )
                        }
                    }
                }
            }
    }

    /**
     * FLASHCAT FORK - the session is withheld until it reports an error (see
     * `RumConfiguration.Builder.setSessionOnError`): its WebView events are handed to RUM, which
     * holds them with the session's own and releases them with it - or on an error raised here.
     * Not under a withdrawn consent: RUM holds nothing of the session then either.
     */
    private fun handOverWithheld(event: JsonObject, datadogContext: DatadogContext, rumContext: RumContext) {
        if (datadogContext.trackingConsent == TrackingConsent.NOT_GRANTED) return
        // Mapped without the replay: a replay withheld with the session is claimed on release.
        val mappedEvent = map(event, datadogContext, rumContext, sessionReplayEnabled = false)
        sdkCore.getFeature(Feature.RUM_FEATURE_NAME)?.sendEvent(
            mapOf(
                "type" to WEB_VIEW_WITHHELD_EVENT_MESSAGE_TYPE,
                WEB_VIEW_EVENT_KEY to mappedEvent
            )
        )
    }

    /**
     * FLASHCAT FORK - an error the web page reported, as opposed to one of the web SDK's own: it
     * releases a replay kept on error like a native error does.
     */
    private fun isSessionError(event: JsonObject): Boolean {
        if (event.get(TYPE_KEY_NAME)?.takeIf { it.isJsonPrimitive }?.asString != ERROR_EVENT_TYPE) return false
        val error = event.get(ERROR_EVENT_TYPE)?.takeIf { it.isJsonObject }?.asJsonObject ?: return true
        return error.get(SOURCE_KEY_NAME)?.takeIf { it.isJsonPrimitive }?.asString != AGENT_ERROR_SOURCE
    }

    private fun map(
        event: JsonObject,
        datadogContext: DatadogContext,
        rumContext: RumContext?,
        sessionReplayEnabled: Boolean
    ): JsonObject {
        try {
            val timeOffset = event.get(VIEW_KEY_NAME)?.asJsonObject?.get(VIEW_ID_KEY_NAME)
                ?.asString?.let { offsetProvider.getOffset(it, datadogContext) } ?: 0L
            return webViewRumEventMapper.mapEvent(event, rumContext, timeOffset, sessionReplayEnabled)
        } catch (e: ClassCastException) {
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
        } catch (e: IllegalStateException) {
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
        return event
    }

    companion object {
        const val VIEW_EVENT_TYPE = "view"
        const val ACTION_EVENT_TYPE = "action"
        const val RESOURCE_EVENT_TYPE = "resource"
        const val ERROR_EVENT_TYPE = "error"
        const val LONG_TASK_EVENT_TYPE = "long_task"
        const val RUM_EVENT_TYPE = "rum"
        const val VIEW_KEY_NAME = "view"
        const val VIEW_ID_KEY_NAME = "id"
        const val JSON_PARSING_ERROR_MESSAGE = "The bundled web RUM event could not be deserialized"
        const val TYPE_KEY_NAME = "type"
        const val SOURCE_KEY_NAME = "source"
        const val AGENT_ERROR_SOURCE = "agent"

        // FLASHCAT FORK - mirror the RUM feature's session state and bus messages.
        const val WITHHELD_SESSION_STATE = "WITHHELD"
        const val WEB_VIEW_WITHHELD_EVENT_MESSAGE_TYPE = "web_view_withheld_event"
        const val WEB_VIEW_ERROR_MESSAGE_TYPE = "web_view_error"
        const val WEB_VIEW_EVENT_KEY = "event"
        const val WEB_VIEW_SESSION_ID_KEY = "sessionId"
        val RUM_EVENT_TYPES = setOf(
            VIEW_EVENT_TYPE,
            ACTION_EVENT_TYPE,
            RESOURCE_EVENT_TYPE,
            LONG_TASK_EVENT_TYPE,
            ERROR_EVENT_TYPE,
            RUM_EVENT_TYPE
        )
    }
}
