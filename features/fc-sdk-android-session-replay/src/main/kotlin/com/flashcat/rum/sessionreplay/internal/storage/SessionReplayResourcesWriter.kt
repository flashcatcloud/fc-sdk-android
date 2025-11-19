/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sessionreplay.internal.storage

import com.flashcat.rum.api.context.FlashcatContext
import com.flashcat.rum.api.feature.Feature
import com.flashcat.rum.api.feature.FeatureSdkCore
import com.flashcat.rum.api.storage.EventType
import com.flashcat.rum.api.storage.RawBatchEvent
import com.flashcat.rum.sessionreplay.internal.processor.EnrichedResource
import com.flashcat.rum.sessionreplay.internal.processor.asBinaryMetadata

internal class SessionReplayResourcesWriter(
    private val sdkCore: FeatureSdkCore
) : ResourcesWriter {
    override fun write(enrichedResource: EnrichedResource) {
        sdkCore.getFeature(Feature.SESSION_REPLAY_RESOURCES_FEATURE_NAME)
            ?.withWriteContext(
                withFeatureContexts = setOf(Feature.RUM_FEATURE_NAME)
            ) { flashcatContext, writeScope ->
                writeScope {
                    synchronized(this@SessionReplayResourcesWriter) {
                        val serializedMetadata = enrichedResource.asBinaryMetadata(flashcatContext.rumApplicationId)
                        it.write(
                            event = RawBatchEvent(
                                data = enrichedResource.resource,
                                metadata = serializedMetadata
                            ),
                            batchMetadata = null,
                            eventType = EventType.DEFAULT
                        )
                    }
                }
            }
    }

    private val FlashcatContext.rumApplicationId: String
        get() = (featuresContext[Feature.RUM_FEATURE_NAME]?.get("application_id") as? String).orEmpty()
}
