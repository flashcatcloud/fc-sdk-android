/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sessionreplay.internal.storage

import com.flashcat.rum.sessionreplay.internal.processor.EnrichedRecord

internal class NoOpRecordWriter : RecordWriter {
    override fun write(record: EnrichedRecord) {
        // no-op
    }
}
