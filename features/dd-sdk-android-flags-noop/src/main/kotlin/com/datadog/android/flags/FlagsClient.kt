/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

import com.datadog.android.Datadog
import com.datadog.android.api.SdkCore
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.FlagsClientState
import com.datadog.android.flags.model.ResolutionDetails
import org.json.JSONObject

/**
 * Client interface for evaluating feature flags and experiments.
 */
@Suppress("UNUSED_PARAMETER")
interface FlagsClient {
    fun setEvaluationContext(context: EvaluationContext, callback: EvaluationContextCallback? = null)
    fun resolveBooleanValue(flagKey: String, defaultValue: Boolean): Boolean
    fun resolveStringValue(flagKey: String, defaultValue: String): String
    fun resolveDoubleValue(flagKey: String, defaultValue: Double): Double
    fun resolveIntValue(flagKey: String, defaultValue: Int): Int
    fun resolveStructureValue(flagKey: String, defaultValue: JSONObject): JSONObject
    fun resolveStructureValue(flagKey: String, defaultValue: Map<String, Any?>): Map<String, Any?>
    fun <T : Any> resolve(flagKey: String, defaultValue: T): ResolutionDetails<T>
    val state: StateObservable

    /**
     * Builder for creating [FlagsClient] instances.
     */
    class Builder @JvmOverloads constructor(
        name: String = DEFAULT_CLIENT_NAME,
        sdkCore: SdkCore = Datadog.getInstance()
    ) {
        fun build(): FlagsClient = NoOpFlagsClient
    }

    /**
     * Companion object providing static access to [FlagsClient] instances.
     */
    companion object {
        private const val DEFAULT_CLIENT_NAME = "default"

        @JvmOverloads
        @JvmStatic
        fun get(name: String = DEFAULT_CLIENT_NAME, sdkCore: SdkCore = Datadog.getInstance()): FlagsClient {
            return NoOpFlagsClient
        }
    }
}

internal object NoOpFlagsClient : FlagsClient {
    override fun setEvaluationContext(context: EvaluationContext, callback: EvaluationContextCallback?) {
        callback?.onSuccess(context)
    }
    override fun resolveBooleanValue(flagKey: String, defaultValue: Boolean): Boolean = defaultValue
    override fun resolveStringValue(flagKey: String, defaultValue: String): String = defaultValue
    override fun resolveDoubleValue(flagKey: String, defaultValue: Double): Double = defaultValue
    override fun resolveIntValue(flagKey: String, defaultValue: Int): Int = defaultValue
    override fun resolveStructureValue(flagKey: String, defaultValue: JSONObject): JSONObject = defaultValue
    override fun resolveStructureValue(flagKey: String, defaultValue: Map<String, Any?>): Map<String, Any?> = defaultValue
    override fun <T : Any> resolve(flagKey: String, defaultValue: T): ResolutionDetails<T> = ResolutionDetails(defaultValue)
    override val state: StateObservable = object : StateObservable {
        override fun getCurrentState(): FlagsClientState = FlagsClientState.READY
        override fun addListener(listener: FlagsStateListener) {}
        override fun removeListener(listener: FlagsStateListener) {}
    }
}
