/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.sessionreplay.internal.storage

import cloud.flashcat.android.api.feature.Feature
import cloud.flashcat.android.api.feature.FeatureSdkCore
import cloud.flashcat.android.api.storage.EventType
import cloud.flashcat.android.api.storage.RawBatchEvent
import cloud.flashcat.android.sessionreplay.internal.RecordCallback
import cloud.flashcat.android.sessionreplay.internal.processor.EnrichedRecord

internal class SessionReplayRecordWriter(
    private val sdkCore: FeatureSdkCore,
    private val recordCallback: RecordCallback
) : RecordWriter {
    override fun write(record: EnrichedRecord) {
        sdkCore.getFeature(Feature.SESSION_REPLAY_FEATURE_NAME)
            ?.withWriteContext { _, writeScope ->
                writeScope {
                    val serializedRecord = record.toJson().toByteArray(Charsets.UTF_8)
                    val rawBatchEvent = RawBatchEvent(data = serializedRecord)
                    synchronized(this@SessionReplayRecordWriter) {
                        val success = it.write(
                            event = rawBatchEvent,
                            batchMetadata = null,
                            eventType = EventType.DEFAULT
                        )
                        if (success) {
                            updateViewSent(record)
                        }
                    }
                }
            }
    }

    private fun updateViewSent(record: EnrichedRecord) {
        /**
         * We have to see whether it's ok that this method is being called from the background.
         * However this gives us the most certainty that the records were actually queued for
         * sending, and not optimized away in the processor. Depending upon the amount of time
         * that it takes to process the nodes, the view may not be relevant anymore.
         */
        recordCallback.onRecordForViewSent(record)
    }
}
