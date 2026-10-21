/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.storage

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.EventType
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.sessionreplay.internal.processor.EnrichedResource
import com.datadog.android.sessionreplay.internal.processor.asBinaryMetadata

internal class SessionReplayResourcesWriter(
    private val sdkCore: FeatureSdkCore
) : ResourcesWriter {

    /**
     * FLASHCAT FORK - kept once found: the core unregisters a feature before stopping it, and the
     * images of a replay released by the stop are written from there.
     */
    @Volatile
    private var featureScope: FeatureScope? = sdkCore.getFeature(Feature.SESSION_REPLAY_RESOURCES_FEATURE_NAME)

    override fun write(enrichedResource: EnrichedResource, sessionId: String, onWritten: () -> Unit) {
        write(enrichedResource, sessionId, onWritten, onSettled = {})
    }

    override fun write(
        enrichedResource: EnrichedResource,
        sessionId: String,
        onWritten: () -> Unit,
        onSettled: () -> Unit
    ) {
        val feature = sdkCore.getFeature(Feature.SESSION_REPLAY_RESOURCES_FEATURE_NAME) ?: featureScope
        if (feature == null) {
            onSettled()
            return
        }
        featureScope = feature
        feature.withWriteContext(
            withFeatureContexts = setOf(Feature.RUM_FEATURE_NAME)
        ) { datadogContext, writeScope ->
            writeIn(datadogContext, writeScope, enrichedResource, onWritten, onSettled)
        }
    }

    override fun captureGranted(onCaptured: (ResourcesWriter?) -> Unit) {
        val feature = sdkCore.getFeature(Feature.SESSION_REPLAY_RESOURCES_FEATURE_NAME) ?: featureScope
        if (feature == null) {
            onCaptured(null)
            return
        }
        featureScope = feature
        feature.withWriteContext(
            withFeatureContexts = setOf(Feature.RUM_FEATURE_NAME)
        ) { datadogContext, writeScope ->
            onCaptured(
                if (datadogContext.trackingConsent == TrackingConsent.GRANTED) {
                    BoundWriter(datadogContext, writeScope)
                } else {
                    null
                }
            )
        }
    }

    private fun writeIn(
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        enrichedResource: EnrichedResource,
        onWritten: () -> Unit,
        onSettled: () -> Unit
    ) {
        writeScope {
            synchronized(this@SessionReplayResourcesWriter) {
                val serializedMetadata = enrichedResource.asBinaryMetadata(datadogContext.rumApplicationId)
                val written = it.write(
                    event = RawBatchEvent(
                        data = enrichedResource.resource,
                        metadata = serializedMetadata
                    ),
                    batchMetadata = null,
                    eventType = EventType.DEFAULT
                )
                // FLASHCAT FORK - a resource counts as sent only once it is in storage.
                if (written) onWritten()
            }
            onSettled()
        }
    }

    /** FLASHCAT FORK - see [captureGranted]. */
    private inner class BoundWriter(
        private val datadogContext: DatadogContext,
        private val writeScope: EventWriteScope
    ) : ResourcesWriter {
        override fun write(enrichedResource: EnrichedResource, sessionId: String, onWritten: () -> Unit) {
            write(enrichedResource, sessionId, onWritten, onSettled = {})
        }

        override fun write(
            enrichedResource: EnrichedResource,
            sessionId: String,
            onWritten: () -> Unit,
            onSettled: () -> Unit
        ) {
            writeIn(datadogContext, writeScope, enrichedResource, onWritten, onSettled)
        }
    }

    private val DatadogContext.rumApplicationId: String
        get() = (featuresContext[Feature.RUM_FEATURE_NAME]?.get("application_id") as? String).orEmpty()
}
