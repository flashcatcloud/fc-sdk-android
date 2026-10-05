/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.storage

import com.datadog.android.sessionreplay.internal.processor.EnrichedRecord

/**
 * Writes the records into the storage.
 */
internal interface RecordWriter {
    /**
     * Writes the record into the storage.
     * @param record to write
     */
    fun write(record: EnrichedRecord)

    /**
     * FLASHCAT FORK - holds the records of this session in memory instead of writing them, until
     * RUM says with [release] or [discard] what became of the session. What another session still
     * holds is kept aside for that word.
     * @param sessionId the RUM session whose replay is kept only if it reports an error
     */
    fun withhold(sessionId: String)

    /**
     * FLASHCAT FORK - the session now current is not held. If it is the session whose records are
     * held, they are written now: it has been released. Otherwise what another session still holds
     * is kept aside for RUM's word.
     * @param sessionId the RUM session now current
     */
    fun stopWithholding(sessionId: String)

    /**
     * FLASHCAT FORK - writes what is held for this session, whether it is still current or has
     * ended since. Nothing happens if nothing is held for it.
     * @param sessionId the RUM session whose events were released
     */
    fun release(sessionId: String)

    /**
     * FLASHCAT FORK - throws away what is held for this session, and whatever of it arrives later.
     * @param sessionId the RUM session that ended without reporting an error
     */
    fun discard(sessionId: String)

    /**
     * FLASHCAT FORK - tracking consent was withdrawn: whatever is held, of any session, is dropped.
     */
    fun dropForConsent()
}
