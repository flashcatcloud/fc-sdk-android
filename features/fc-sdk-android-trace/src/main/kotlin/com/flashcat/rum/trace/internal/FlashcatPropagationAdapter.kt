/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.trace.internal

import com.flashcat.rum.api.InternalLogger
import com.flashcat.rum.trace.api.propagation.DatadogPropagation
import com.flashcat.rum.trace.api.span.DatadogSpanContext
import com.datadog.trace.bootstrap.instrumentation.api.AgentPropagation
import kotlin.reflect.KClass

internal class DatadogPropagationAdapter(
    private val internalLogger: InternalLogger,
    private val delegate: AgentPropagation
) : DatadogPropagation {

    override fun <C> inject(
        context: DatadogSpanContext,
        carrier: C,
        setter: (carrier: C, key: String, value: String) -> Unit
    ) {
        if (context !is DatadogSpanContextAdapter) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { constructErrorMessage(context::class) }
            )
            return
        }
        delegate.inject(context.delegate, carrier, setter)
    }

    override fun <C> extract(
        carrier: C,
        getter: (carrier: C, classifier: (String, String) -> Boolean) -> Unit
    ): DatadogSpanContext? {
        return delegate.extract(carrier) { car, cls -> getter(car, cls::accept) }
            ?.let { DatadogSpanContextAdapter(it) }
    }

    private fun constructErrorMessage(klass: KClass<*>) = "FlashcatPropagationAdapter supports only" +
        " DatadogSpanContextAdapter instances for injection but ${klass.simpleName} is given"
}
