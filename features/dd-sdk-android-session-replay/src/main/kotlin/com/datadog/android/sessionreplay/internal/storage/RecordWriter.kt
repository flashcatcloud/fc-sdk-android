/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.storage

import com.datadog.android.sessionreplay.internal.processor.EnrichedRecord

/**
 * Will persists the serialized EnrichedRecord in the allocated Session Replay caching location.
 */
internal interface RecordWriter {
    /**
     * Writes the record to disk.
     * @param record to write
     */
    fun write(record: EnrichedRecord)

    /**
     * FLASHCAT FORK - holds the records of this session in memory instead of writing them, until
     * [stopWithholding] releases them.
     * @param sessionId the RUM session whose replay is kept only if it reports an error
     */
    fun withhold(sessionId: String)

    /**
     * FLASHCAT FORK - stops holding records.
     * @param releasedSessionId the session whose held records are written now; held records of
     * any other session are thrown away. Null throws away whatever is held.
     */
    fun stopWithholding(releasedSessionId: String?)
}
