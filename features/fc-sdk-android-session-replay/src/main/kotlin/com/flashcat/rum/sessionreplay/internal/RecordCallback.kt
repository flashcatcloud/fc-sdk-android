/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sessionreplay.internal

import com.flashcat.rum.sessionreplay.internal.processor.EnrichedRecord

/**
 * Notifies the receiver whenever the screen is recorded or not.
 * For internal usage only.
 */
internal interface RecordCallback {

    /**
     * Notifies when a view session replay record was sent.
     * @param record as [EnrichedRecord]
     */
    fun onRecordForViewSent(record: EnrichedRecord)
}
