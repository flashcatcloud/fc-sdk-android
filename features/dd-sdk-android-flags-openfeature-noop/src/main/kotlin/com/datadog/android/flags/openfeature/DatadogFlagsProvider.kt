/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.openfeature

import com.datadog.android.flags.FlagsClient
import dev.openfeature.kotlin.sdk.EvaluationContext
import dev.openfeature.kotlin.sdk.FeatureProvider
import dev.openfeature.kotlin.sdk.Hook
import dev.openfeature.kotlin.sdk.ProviderEvaluation
import dev.openfeature.kotlin.sdk.ProviderMetadata
import dev.openfeature.kotlin.sdk.Value
import dev.openfeature.kotlin.sdk.events.OpenFeatureProviderEvents
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * OpenFeature [FeatureProvider] implementation backed by Datadog Feature Flags.
 */
@Suppress("UNUSED_PARAMETER")
class DatadogFlagsProvider private constructor(private val flagsClient: FlagsClient) :
    FeatureProvider {

    override val metadata: ProviderMetadata = object : ProviderMetadata {
        override val name: String = PROVIDER_NAME
    }

    override val hooks: List<Hook<*>> = emptyList()

    override suspend fun initialize(initialContext: EvaluationContext?) {
        // no-op
    }

    override suspend fun onContextSet(
        oldContext: EvaluationContext?,
        newContext: EvaluationContext
    ) {
        // no-op
    }

    override fun getBooleanEvaluation(
        key: String,
        defaultValue: Boolean,
        context: EvaluationContext?
    ): ProviderEvaluation<Boolean> = ProviderEvaluation(defaultValue)

    override fun getStringEvaluation(
        key: String,
        defaultValue: String,
        context: EvaluationContext?
    ): ProviderEvaluation<String> = ProviderEvaluation(defaultValue)

    override fun getIntegerEvaluation(
        key: String,
        defaultValue: Int,
        context: EvaluationContext?
    ): ProviderEvaluation<Int> = ProviderEvaluation(defaultValue)

    override fun getDoubleEvaluation(
        key: String,
        defaultValue: Double,
        context: EvaluationContext?
    ): ProviderEvaluation<Double> = ProviderEvaluation(defaultValue)

    override fun getObjectEvaluation(
        key: String,
        defaultValue: Value,
        context: EvaluationContext?
    ): ProviderEvaluation<Value> = ProviderEvaluation(defaultValue)

    override fun shutdown() {
        // no-op
    }

    override fun observe(): Flow<OpenFeatureProviderEvents> = emptyFlow()

    companion object {
        private const val PROVIDER_NAME = "Datadog Feature Flags Provider"

        internal fun wrap(
            flagsClient: FlagsClient
        ): DatadogFlagsProvider = DatadogFlagsProvider(flagsClient)
    }
}
