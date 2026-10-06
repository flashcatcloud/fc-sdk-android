/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event

import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.core.internal.persistence.Deserializer
import kotlin.math.max

internal class RumViewEventFilter(
    private val eventMetaDeserializer: Deserializer<ByteArray, RumEventMeta>
) {

    fun filterOutRedundantViewEvents(batch: List<RawBatchEvent>): List<RawBatchEvent> {
        val maxDocVersionByViewId = mutableMapOf<String, Long>()
        val viewMetaByEvent = mutableMapOf<RawBatchEvent, RumEventMeta.View>()

        batch.forEach {
            val eventMeta = eventMetaDeserializer.deserialize(it.metadata)
            if (eventMeta is RumEventMeta.View) {
                viewMetaByEvent += it to eventMeta
                val viewId = eventMeta.viewId
                val documentVersion = eventMeta.documentVersion
                val maxDocVersionSeen = maxDocVersionByViewId[viewId]
                if (maxDocVersionSeen == null) {
                    maxDocVersionByViewId[viewId] = documentVersion
                } else {
                    maxDocVersionByViewId[viewId] = max(documentVersion, maxDocVersionSeen)
                }
            }
        }

        // FLASHCAT FORK - the surviving version of a view takes the place of the view's first
        // occurrence rather than staying where it was written. A session kept on error releases its
        // views first, oldest first, and their live updates follow in the same batch: keeping each
        // latest version where it was written would push the views behind the events they contain
        // and out of start order, while the intake builds the session out of the first view it sees.
        val latestByViewId = mutableMapOf<String, RawBatchEvent>()
        viewMetaByEvent.forEach { (event, viewMeta) ->
            @Suppress("UnsafeThirdPartyFunctionCall") // if there is a meta, there is a max doc version
            if (viewMeta.documentVersion == maxDocVersionByViewId.getValue(viewMeta.viewId)) {
                latestByViewId[viewMeta.viewId] = event
            }
        }
        val placed = mutableSetOf<String>()
        val emitted = mutableSetOf<RawBatchEvent>()
        return batch.mapNotNull {
            val viewMeta = viewMetaByEvent[it]
            when {
                viewMeta == null -> it
                // we need to leave only view events with accessibility OR view event with a max doc version
                // for a give viewId in the batch, because backend will do the same during the reduce process
                viewMeta.hasAccessibility == true -> it.takeIf { event -> emitted.add(event) }
                placed.add(viewMeta.viewId) -> latestByViewId[viewMeta.viewId]?.takeIf { event -> emitted.add(event) }
                else -> null
            }
        }
    }
}
