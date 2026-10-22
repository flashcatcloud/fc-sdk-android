/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.storage

import com.datadog.android.sessionreplay.internal.processor.EnrichedResource

internal interface ResourcesWriter {
    /**
     * Writes the resource to disk.
     * @param enrichedResource to write
     * @param sessionId the RUM session the resource was captured in (FLASHCAT FORK - so a resource
     * of a session whose replay is withheld is held with its records)
     * @param onWritten called once the resource is handed to storage, which is when it counts as sent
     */
    fun write(enrichedResource: EnrichedResource, sessionId: String, onWritten: () -> Unit)
}
