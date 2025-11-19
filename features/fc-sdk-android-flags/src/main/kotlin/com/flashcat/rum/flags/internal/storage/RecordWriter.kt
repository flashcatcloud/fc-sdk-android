/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.flags.internal.storage

import com.flashcat.rum.flags.model.ExposureEvent

/**
 * Will persists the serialized [ExposureEvent] in the allocated caching location.
 */
internal interface RecordWriter {
    /**
     * Writes the record to disk.
     * @param record to write
     */
    fun write(record: ExposureEvent)
}
