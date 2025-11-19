/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.okhttp.trace

import androidx.annotation.FloatRange
import com.flashcat.rum.core.sampling.DeterministicSampler
import com.flashcat.rum.okhttp.internal.utils.SpanSamplingIdProvider
import com.flashcat.rum.trace.api.span.DatadogSpan

/**
 * A [DeterministicSampler] using the TraceID of a Span to compute the sampling decision.
 *
 * @param sampleRateProvider Provider for the sample rate value which will be called each time
 * the sampling decision needs to be made. All the values should be in the range [0;100].
 */
open class DeterministicTraceSampler(
    sampleRateProvider: () -> Float
) : DeterministicSampler<DatadogSpan>(
    SpanSamplingIdProvider::provideId,
    sampleRateProvider
) {

    /**
     * Creates a new instance lof [DeterministicSampler] with the given sample rate.
     *
     * @param sampleRate Sample rate to use.
     */
    constructor(
        @FloatRange(from = 0.0, to = 100.0) sampleRate: Float
    ) : this({ sampleRate })

    /**
     * Creates a new instance of [DeterministicSampler] with the given sample rate.
     *
     * @param sampleRate Sample rate to use.
     */
    constructor(
        @FloatRange(from = 0.0, to = 100.0) sampleRate: Double
    ) : this(sampleRate.toFloat())
}
