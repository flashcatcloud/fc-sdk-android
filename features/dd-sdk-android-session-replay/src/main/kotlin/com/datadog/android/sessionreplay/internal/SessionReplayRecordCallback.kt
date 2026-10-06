/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal

import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.sessionreplay.internal.processor.EnrichedRecord

internal class SessionReplayRecordCallback(
    private val featureSdkCore: FeatureSdkCore
) : RecordCallback {

    @Suppress("UNCHECKED_CAST")
    override fun onRecordForViewSent(record: EnrichedRecord) {
        val recordsSize = record.records.size
        if (recordsSize > 0) {
            // we are already past the event processing pipeline and this method is called from write stage, we can
            // update directly from this thread, given we patially mutate context.
            featureSdkCore.updateFeatureContext(Feature.SESSION_REPLAY_FEATURE_NAME, useContextThread = false) {
                val viewId = record.viewId
                val viewMetadata = (it[viewId] as? MutableMap<String, Any?>) ?: mutableMapOf()
                viewMetadata[HAS_REPLAY_KEY] = true
                updateRecordsCount(viewMetadata, recordsSize)
                it[viewId] = viewMetadata
            }
        }
    }

    // FLASHCAT FORK - held records are counted apart, under a key RUM reads only to claim the
    // replay for events released alongside them: `has_replay` and `records_count` stay for records
    // actually sent.
    @Suppress("UNCHECKED_CAST")
    override fun onRecordForViewWithheld(record: EnrichedRecord) {
        val recordsSize = record.records.size
        if (recordsSize == 0) return
        featureSdkCore.updateFeatureContext(Feature.SESSION_REPLAY_FEATURE_NAME, useContextThread = false) {
            val viewMetadata = (it[record.viewId] as? MutableMap<String, Any?>) ?: mutableMapOf()
            viewMetadata[VIEW_WITHHELD_RECORDS_COUNT_KEY] =
                (viewMetadata[VIEW_WITHHELD_RECORDS_COUNT_KEY] as? Long ?: 0L) + recordsSize
            it[record.viewId] = viewMetadata
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun onWithheldRecordsCleared(records: List<EnrichedRecord>) {
        val clearedByView = records.groupBy { it.viewId }
            .mapValues { (_, cleared) -> cleared.sumOf { it.records.size } }
        if (clearedByView.values.all { it == 0 }) return
        featureSdkCore.updateFeatureContext(Feature.SESSION_REPLAY_FEATURE_NAME, useContextThread = false) {
            clearedByView.forEach { (viewId, cleared) ->
                val viewMetadata = it[viewId] as? MutableMap<String, Any?> ?: return@forEach
                val remaining = (viewMetadata[VIEW_WITHHELD_RECORDS_COUNT_KEY] as? Long ?: 0L) - cleared
                if (remaining > 0) {
                    viewMetadata[VIEW_WITHHELD_RECORDS_COUNT_KEY] = remaining
                } else {
                    viewMetadata.remove(VIEW_WITHHELD_RECORDS_COUNT_KEY)
                    // RUM leaves the entry of a completed view in place while its session is
                    // withheld; nothing sent and nothing held means nothing to keep it for.
                    if (viewMetadata.isEmpty()) it.remove(viewId)
                }
            }
        }
    }

    private fun updateRecordsCount(
        viewMetadata: MutableMap<String, Any?>,
        recordsCount: Int
    ) {
        val currentRecords = viewMetadata[VIEW_RECORDS_COUNT_KEY] as? Long ?: 0
        val newRecords = currentRecords + recordsCount
        viewMetadata[VIEW_RECORDS_COUNT_KEY] = newRecords
    }

    companion object {
        internal const val HAS_REPLAY_KEY = "has_replay"
        internal const val VIEW_RECORDS_COUNT_KEY = "records_count"
        internal const val VIEW_WITHHELD_RECORDS_COUNT_KEY = "withheld_records_count"
    }
}
