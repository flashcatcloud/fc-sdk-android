/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sqldelight.internal

import com.flashcat.rum.sqldelight.TransactionWithSpanAndWithoutReturn
import com.flashcat.rum.trace.api.span.DatadogSpan
import com.squareup.sqldelight.TransactionWithoutReturn

internal class TransactionWithSpanAndWithoutReturnImpl(
    private val span: DatadogSpan,
    private val transaction: TransactionWithoutReturn
) : DatadogSpan by span,
    TransactionWithSpanAndWithoutReturn,
    TransactionWithoutReturn by transaction
