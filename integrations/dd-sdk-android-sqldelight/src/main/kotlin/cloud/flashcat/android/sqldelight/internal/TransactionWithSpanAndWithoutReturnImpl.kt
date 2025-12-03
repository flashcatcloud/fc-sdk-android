/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.sqldelight.internal

import cloud.flashcat.android.sqldelight.TransactionWithSpanAndWithoutReturn
import cloud.flashcat.android.trace.api.span.DatadogSpan
import com.squareup.sqldelight.TransactionWithoutReturn

internal class TransactionWithSpanAndWithoutReturnImpl(
    private val span: DatadogSpan,
    private val transaction: TransactionWithoutReturn
) : DatadogSpan by span,
    TransactionWithSpanAndWithoutReturn,
    TransactionWithoutReturn by transaction
