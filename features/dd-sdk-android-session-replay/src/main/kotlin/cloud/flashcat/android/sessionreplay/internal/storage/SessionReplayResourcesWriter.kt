/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.sessionreplay.internal.storage

import cloud.flashcat.android.api.context.DatadogContext
import cloud.flashcat.android.api.feature.Feature
import cloud.flashcat.android.api.feature.FeatureSdkCore
import cloud.flashcat.android.api.storage.EventType
import cloud.flashcat.android.api.storage.RawBatchEvent
import cloud.flashcat.android.sessionreplay.internal.processor.EnrichedResource
import cloud.flashcat.android.sessionreplay.internal.processor.asBinaryMetadata

internal class SessionReplayResourcesWriter(
    private val sdkCore: FeatureSdkCore
) : ResourcesWriter {
    override fun write(enrichedResource: EnrichedResource) {
        sdkCore.getFeature(Feature.SESSION_REPLAY_RESOURCES_FEATURE_NAME)
            ?.withWriteContext(
                withFeatureContexts = setOf(Feature.RUM_FEATURE_NAME)
            ) { datadogContext, writeScope ->
                writeScope {
                    synchronized(this@SessionReplayResourcesWriter) {
                        val serializedMetadata = enrichedResource.asBinaryMetadata(datadogContext.rumApplicationId)
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

    private val DatadogContext.rumApplicationId: String
        get() = (featuresContext[Feature.RUM_FEATURE_NAME]?.get("application_id") as? String).orEmpty()
}
