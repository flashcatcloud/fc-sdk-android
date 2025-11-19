/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum

import com.flashcat.rum.api.SdkCore
import com.flashcat.rum.api.feature.Feature
import com.flashcat.rum.api.feature.FeatureScope
import com.flashcat.rum.api.feature.FeatureSdkCore
import com.flashcat.rum.core.configuration.Configuration
import com.flashcat.rum.core.internal.FlashcatCore
import com.flashcat.rum.internal.telemetry.InternalTelemetryEvent
import com.flashcat.rum.lint.InternalApi

/**
 * This class exposes internal methods that are used by other Datadog modules and cross platform
 * frameworks. It is not meant for public use.
 *
 * DO NOT USE this class or its methods if you are not working on the internals of the Flashcat SDK
 * or one of the cross platform frameworks.
 *
 * Methods, members, and functionality of this class  are subject to change without notice, as they
 * are not considered part of the public interface of the Flashcat SDK.
 */
@InternalApi
@Suppress(
    "UndocumentedPublicClass",
    "UndocumentedPublicFunction",
    "UndocumentedPublicProperty",
    "ClassName",
    "ClassNaming",
    "VariableNaming"
)
class _InternalProxy internal constructor(
    private val sdkCore: SdkCore
) {
    @Suppress("StringLiteralDuplication")
    class _TelemetryProxy internal constructor(private val sdkCore: SdkCore) {

        private val rumFeature: FeatureScope?
            get() {
                return (sdkCore as? FeatureSdkCore)?.getFeature(Feature.RUM_FEATURE_NAME)
            }

        fun debug(message: String) {
            val telemetryEvent = InternalTelemetryEvent.Log.Debug(
                message = message,
                additionalProperties = null
            )
            rumFeature?.sendEvent(telemetryEvent)
        }

        fun error(message: String, throwable: Throwable? = null) {
            val telemetryEvent = InternalTelemetryEvent.Log.Error(
                message = message,
                error = throwable
            )
            rumFeature?.sendEvent(telemetryEvent)
        }

        fun error(message: String, stack: String?, kind: String?) {
            val telemetryEvent = InternalTelemetryEvent.Log.Error(
                message = message,
                stacktrace = stack,
                kind = kind
            )
            rumFeature?.sendEvent(telemetryEvent)
        }
    }

    @Suppress("PropertyName")
    val _telemetry: _TelemetryProxy = _TelemetryProxy(sdkCore)

    fun setCustomAppVersion(version: String) {
        val coreFeature = (sdkCore as? FlashcatCore)?.coreFeature
        coreFeature?.packageVersionProvider?.version = version
    }

    companion object {
        // TODO RUM-368 Expose it as public API? Needed for the integration tests at least,
        //  because OkHttp MockWebServer is HTTP based
        fun allowClearTextHttp(builder: Configuration.Builder): Configuration.Builder {
            return builder.allowClearTextHttp()
        }
    }
}
