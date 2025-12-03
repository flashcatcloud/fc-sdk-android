/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.trace.integration.otel

import cloud.flashcat.android.api.feature.Feature
import cloud.flashcat.android.api.feature.FeatureScope
import cloud.flashcat.android.trace.integration.tests.utils.BlockingWriterWrapper
import cloud.flashcat.tools.unit.getFieldValue
import cloud.flashcat.tools.unit.setFieldValue
import cloud.flashcat.trace.common.writer.Writer

private const val WRITER_FIELD_NAME = "coreTracerDataWriter"

internal fun FeatureScope.useBlockingWriter(): BlockingWriterWrapper {
    val feature = this.unwrap<Feature>()
    val writer: Writer = feature.getFieldValue(WRITER_FIELD_NAME)
    return if (writer is BlockingWriterWrapper) {
        writer
    } else {
        val blockingWriterWrapper = BlockingWriterWrapper(writer)
        feature.setFieldValue(WRITER_FIELD_NAME, blockingWriterWrapper)
        blockingWriterWrapper
    }
}
