/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.telemetry.internal

import androidx.annotation.AnyThread
import com.flashcat.rum.api.InternalLogger
import com.flashcat.rum.api.context.FlashcatContext
import com.flashcat.rum.api.feature.Feature
import com.flashcat.rum.api.storage.DataWriter
import com.flashcat.rum.api.storage.EventType
import com.flashcat.rum.core.InternalSdkCore
import com.flashcat.rum.core.sampling.RateBasedSampler
import com.flashcat.rum.core.sampling.Sampler
import com.flashcat.rum.internal.attributes.LocalAttribute
import com.flashcat.rum.internal.telemetry.InternalTelemetryEvent
import com.flashcat.rum.internal.telemetry.TracingHeaderTypesSet
import com.flashcat.rum.rum.RumSessionListener
import com.flashcat.rum.rum.internal.RumFeature
import com.flashcat.rum.rum.internal.RumFeature.Configuration
import com.flashcat.rum.rum.internal.domain.RumContext
import com.flashcat.rum.rum.internal.domain.scope.RumRawEvent
import com.flashcat.rum.rum.internal.metric.SessionMetricDispatcher
import com.flashcat.rum.rum.internal.utils.HUNDRED
import com.flashcat.rum.rum.internal.utils.percent
import com.flashcat.rum.rum.metric.interactiontonextview.TimeBasedInteractionIdentifier
import com.flashcat.rum.rum.metric.networksettled.TimeBasedInitialResourceIdentifier
import com.flashcat.rum.rum.tracking.ActivityViewTrackingStrategy
import com.flashcat.rum.rum.tracking.FragmentViewTrackingStrategy
import com.flashcat.rum.rum.tracking.MixedViewTrackingStrategy
import com.flashcat.rum.rum.tracking.NavigationViewTrackingStrategy
import com.flashcat.rum.telemetry.model.TelemetryConfigurationEvent
import com.flashcat.rum.telemetry.model.TelemetryDebugEvent
import com.flashcat.rum.telemetry.model.TelemetryErrorEvent
import com.flashcat.rum.telemetry.model.TelemetryUsageEvent
import com.flashcat.rum.telemetry.model.TelemetryUsageEvent.ActionType
import java.util.Locale
import com.flashcat.rum.telemetry.model.TelemetryConfigurationEvent.ViewTrackingStrategy as VTS

@Suppress("TooManyFunctions")
internal class TelemetryEventHandler(
    internal val sdkCore: InternalSdkCore,
    internal val eventSampler: Sampler<InternalTelemetryEvent>,
    internal val configurationExtraSampler: Sampler<InternalTelemetryEvent> =
        RateBasedSampler(DEFAULT_CONFIGURATION_SAMPLE_RATE),
    private val sessionEndedMetricDispatcher: SessionMetricDispatcher,
    private val maxEventCountPerSession: Int = MAX_EVENTS_PER_SESSION
) : RumSessionListener {

    private var trackNetworkRequests = false

    private val eventIDsSeenInCurrentSession = mutableSetOf<TelemetryEventId>()
    private var totalEventsSeenInCurrentSession = 0

    private val rumConfig: Configuration?
        get() = sdkCore.getFeature(Feature.RUM_FEATURE_NAME)
            ?.unwrap<RumFeature>()
            ?.configuration

    @AnyThread
    @Suppress("LongMethod")
    fun handleEvent(
        wrappedEvent: RumRawEvent.TelemetryEventWrapper,
        writer: DataWriter<Any>
    ) {
        val event = wrappedEvent.event
        if (!canWrite(event)) return

        eventIDsSeenInCurrentSession.add(event.identity)
        totalEventsSeenInCurrentSession++
        sdkCore.getFeature(Feature.RUM_FEATURE_NAME)?.withWriteContext(
            withFeatureContexts = setOf(
                Feature.SESSION_REPLAY_FEATURE_NAME,
                Feature.TRACING_FEATURE_NAME,
                Feature.RUM_FEATURE_NAME
            )
        ) { flashcatContext, writeScope ->
            val timestamp = wrappedEvent.eventTime.timestamp + flashcatContext.time.serverTimeOffsetMs
            val telemetryEvent: Any? = when (event) {
                is InternalTelemetryEvent.Log.Debug -> {
                    createDebugEvent(
                        flashcatContext = flashcatContext,
                        timestamp = timestamp,
                        message = event.message,
                        additionalProperties = event.additionalProperties,
                        effectiveSampleRate = computeEffectiveSampleRate(event.additionalProperties)
                    )
                }

                is InternalTelemetryEvent.Metric -> {
                    createDebugEvent(
                        flashcatContext = flashcatContext,
                        timestamp = timestamp,
                        message = event.message,
                        additionalProperties = event.additionalProperties,
                        effectiveSampleRate = computeEffectiveSampleRate(event.additionalProperties)
                    )
                }

                is InternalTelemetryEvent.Log.Error -> {
                    sessionEndedMetricDispatcher.onSdkErrorTracked(
                        sessionId = flashcatContext.rumContext().sessionId,
                        errorKind = event.kind
                    )
                    createErrorEvent(
                        flashcatContext = flashcatContext,
                        timestamp = timestamp,
                        message = event.message,
                        stack = event.resolveStacktrace(),
                        kind = event.resolveKind(),
                        additionalProperties = event.additionalProperties,
                        effectiveSampleRate = computeEffectiveSampleRate(event.additionalProperties)
                    )
                }

                is InternalTelemetryEvent.Configuration -> {
                    createConfigurationEvent(
                        flashcatContext = flashcatContext,
                        timestamp = timestamp,
                        event = event,
                        effectiveSampleRate = computeEffectiveSampleRate(
                            eventSpecificSamplingRate = rumConfig?.telemetryConfigurationSampleRate
                        )
                    )
                }

                is InternalTelemetryEvent.ApiUsage -> {
                    createApiUsageEvent(
                        flashcatContext = flashcatContext,
                        timestamp = timestamp,
                        event = event,
                        effectiveSampleRate = computeEffectiveSampleRate(event.additionalProperties)
                    )
                }

                is InternalTelemetryEvent.InterceptorInstantiated -> {
                    trackNetworkRequests = true
                    null
                }
            }
            if (telemetryEvent != null) {
                writeScope {
                    writer.write(it, telemetryEvent, EventType.TELEMETRY)
                }
            }
        }
    }

    override fun onSessionStarted(sessionId: String, isDiscarded: Boolean) {
        eventIDsSeenInCurrentSession.clear()
        totalEventsSeenInCurrentSession = 0
    }

    // region Internal

    @Suppress("ReturnCount")
    private fun canWrite(event: InternalTelemetryEvent): Boolean {
        if (!eventSampler.sample(event)) return false

        if (event is InternalTelemetryEvent.Configuration && !configurationExtraSampler.sample(event)) {
            return false
        }

        val eventIdentity = event.identity

        if (isLog(event) && eventIDsSeenInCurrentSession.contains(eventIdentity)) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.MAINTAINER,
                { ALREADY_SEEN_EVENT_MESSAGE.format(Locale.US, eventIdentity) }
            )
            return false
        }

        if (totalEventsSeenInCurrentSession >= maxEventCountPerSession) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.MAINTAINER,
                { MAX_EVENT_NUMBER_REACHED_MESSAGE }
            )
            return false
        }

        return true
    }

    private fun isLog(event: InternalTelemetryEvent): Boolean {
        return event is InternalTelemetryEvent.Log
    }

    private fun createDebugEvent(
        flashcatContext: FlashcatContext,
        timestamp: Long,
        message: String,
        additionalProperties: Map<String, Any?>?,
        effectiveSampleRate: Float
    ): TelemetryDebugEvent {
        val rumContext = flashcatContext.rumContext()
        val resolvedAdditionalProperties = additionalProperties.orEmpty()
            .toMutableMap()
            .cleanUpInternalAttributes()

        return TelemetryDebugEvent(
            dd = TelemetryDebugEvent.Dd(),
            date = timestamp,
            source = TelemetryDebugEvent.Source.tryFromSource(
                flashcatContext.source,
                sdkCore.internalLogger
            ) ?: TelemetryDebugEvent.Source.ANDROID,
            service = TELEMETRY_SERVICE_NAME,
            version = flashcatContext.sdkVersion,
            effectiveSampleRate = effectiveSampleRate,
            application = TelemetryDebugEvent.Application(rumContext.applicationId),
            session = TelemetryDebugEvent.Session(rumContext.sessionId),
            view = rumContext.viewId?.let { TelemetryDebugEvent.View(it) },
            action = rumContext.actionId?.let { TelemetryDebugEvent.Action(it) },
            telemetry = TelemetryDebugEvent.Telemetry(
                message = message,
                additionalProperties = resolvedAdditionalProperties,
                device = TelemetryDebugEvent.Device(
                    architecture = flashcatContext.deviceInfo.architecture,
                    brand = flashcatContext.deviceInfo.deviceBrand,
                    model = flashcatContext.deviceInfo.deviceModel
                ),
                os = TelemetryDebugEvent.Os(
                    build = flashcatContext.deviceInfo.deviceBuildId,
                    version = flashcatContext.deviceInfo.osVersion,
                    name = flashcatContext.deviceInfo.osName
                )
            )
        )
    }

    @Suppress("LongParameterList")
    private fun createErrorEvent(
        flashcatContext: FlashcatContext,
        timestamp: Long,
        message: String,
        stack: String?,
        kind: String?,
        effectiveSampleRate: Float,
        additionalProperties: Map<String, Any?>?
    ): TelemetryErrorEvent {
        val rumContext = flashcatContext.rumContext()
        val resolvedAdditionalProperties = additionalProperties.orEmpty()
            .toMutableMap()
            .cleanUpInternalAttributes()

        return TelemetryErrorEvent(
            dd = TelemetryErrorEvent.Dd(),
            date = timestamp,
            source = TelemetryErrorEvent.Source.tryFromSource(
                flashcatContext.source,
                sdkCore.internalLogger
            ) ?: TelemetryErrorEvent.Source.ANDROID,
            service = TELEMETRY_SERVICE_NAME,
            version = flashcatContext.sdkVersion,
            application = TelemetryErrorEvent.Application(rumContext.applicationId),
            session = TelemetryErrorEvent.Session(rumContext.sessionId),
            view = rumContext.viewId?.let { TelemetryErrorEvent.View(it) },
            action = rumContext.actionId?.let { TelemetryErrorEvent.Action(it) },
            effectiveSampleRate = effectiveSampleRate,
            telemetry = TelemetryErrorEvent.Telemetry(
                message = message,
                additionalProperties = resolvedAdditionalProperties,
                error = if (stack != null || kind != null) {
                    TelemetryErrorEvent.Error(
                        stack = stack,
                        kind = kind
                    )
                } else {
                    null
                },
                device = TelemetryErrorEvent.Device(
                    architecture = flashcatContext.deviceInfo.architecture,
                    brand = flashcatContext.deviceInfo.deviceBrand,
                    model = flashcatContext.deviceInfo.deviceModel
                ),
                os = TelemetryErrorEvent.Os(
                    build = flashcatContext.deviceInfo.deviceBuildId,
                    version = flashcatContext.deviceInfo.osVersion,
                    name = flashcatContext.deviceInfo.osName
                )
            )
        )
    }

    @Suppress("LongMethod")
    private fun createConfigurationEvent(
        flashcatContext: FlashcatContext,
        timestamp: Long,
        event: InternalTelemetryEvent.Configuration,
        effectiveSampleRate: Float
    ): TelemetryConfigurationEvent {
        val traceFeature = sdkCore.getFeature(Feature.TRACING_FEATURE_NAME)
        val sessionReplayFeatureContext = flashcatContext.featuresContext[Feature.SESSION_REPLAY_FEATURE_NAME].orEmpty()
        val sessionReplaySampleRate = sessionReplayFeatureContext[SESSION_REPLAY_SAMPLE_RATE_KEY]
            as? Long
        val startRecordingImmediately =
            sessionReplayFeatureContext[SESSION_REPLAY_START_IMMEDIATE_RECORDING_KEY] as? Boolean
        val sessionReplayImagePrivacy =
            sessionReplayFeatureContext[SESSION_REPLAY_IMAGE_PRIVACY_KEY] as? String
        val sessionReplayTouchPrivacy =
            sessionReplayFeatureContext[SESSION_REPLAY_TOUCH_PRIVACY_KEY] as? String
        val sessionReplayTextAndInputPrivacy =
            sessionReplayFeatureContext[SESSION_REPLAY_TEXT_AND_INPUT_PRIVACY_KEY] as? String
        val viewTrackingStrategy = when (rumConfig?.viewTrackingStrategy) {
            is ActivityViewTrackingStrategy -> VTS.ACTIVITYVIEWTRACKINGSTRATEGY
            is FragmentViewTrackingStrategy -> VTS.FRAGMENTVIEWTRACKINGSTRATEGY
            is MixedViewTrackingStrategy -> VTS.MIXEDVIEWTRACKINGSTRATEGY
            is NavigationViewTrackingStrategy -> VTS.NAVIGATIONVIEWTRACKINGSTRATEGY
            else -> null
        }

        val rumContext = flashcatContext.rumContext()
        val traceContext = flashcatContext.featuresContext[Feature.TRACING_FEATURE_NAME].orEmpty()
        val tracerApi = resolveTracerApi(traceContext)
        val openTelemetryApiVersion = resolveOpenTelemetryApiVersion(tracerApi, traceContext)
        val useTracing = (traceFeature != null && tracerApi != null)

        val okhttpInterceptorSampleRate = traceContext[OKHTTP_INTERCEPTOR_SAMPLE_RATE] as? Float?
        val tracingHeaderTypes =
            traceContext[OKHTTP_INTERCEPTOR_HEADER_TYPES] as? TracingHeaderTypesSet

        val invTimeBasedThreshold = (rumConfig?.lastInteractionIdentifier as? TimeBasedInteractionIdentifier)
            ?.timeThresholdInMilliseconds
        val tnsTimeBasedThreshold = (rumConfig?.initialResourceIdentifier as? TimeBasedInitialResourceIdentifier)
            ?.timeThresholdInMilliseconds

        return TelemetryConfigurationEvent(
            dd = TelemetryConfigurationEvent.Dd(),
            date = timestamp,
            service = TELEMETRY_SERVICE_NAME,
            source = TelemetryConfigurationEvent.Source.tryFromSource(
                flashcatContext.source,
                sdkCore.internalLogger
            ) ?: TelemetryConfigurationEvent.Source.ANDROID,
            version = flashcatContext.sdkVersion,
            application = TelemetryConfigurationEvent.Application(rumContext.applicationId),
            session = TelemetryConfigurationEvent.Session(rumContext.sessionId),
            view = rumContext.viewId?.let { TelemetryConfigurationEvent.View(it) },
            action = rumContext.actionId?.let { TelemetryConfigurationEvent.Action(it) },
            experimentalFeatures = null,
            effectiveSampleRate = effectiveSampleRate,
            telemetry = TelemetryConfigurationEvent.Telemetry(
                device = TelemetryConfigurationEvent.Device(
                    architecture = flashcatContext.deviceInfo.architecture,
                    brand = flashcatContext.deviceInfo.deviceBrand,
                    model = flashcatContext.deviceInfo.deviceModel
                ),
                os = TelemetryConfigurationEvent.Os(
                    build = flashcatContext.deviceInfo.deviceBuildId,
                    version = flashcatContext.deviceInfo.osVersion,
                    name = flashcatContext.deviceInfo.osName
                ),
                configuration = TelemetryConfigurationEvent.Configuration(
                    sessionSampleRate = rumConfig?.sampleRate?.toLong(),
                    telemetrySampleRate = rumConfig?.telemetrySampleRate?.toLong(),
                    useProxy = event.useProxy,
                    trackFrustrations = rumConfig?.trackFrustrations,
                    useLocalEncryption = event.useLocalEncryption,
                    viewTrackingStrategy = viewTrackingStrategy,
                    trackBackgroundEvents = rumConfig?.backgroundEventTracking,
                    trackInteractions = rumConfig?.userActionTracking != null,
                    trackErrors = event.trackErrors,
                    trackNativeLongTasks = rumConfig?.longTaskTrackingStrategy != null,
                    batchSize = event.batchSize,
                    batchUploadFrequency = event.batchUploadFrequency,
                    mobileVitalsUpdatePeriod = rumConfig?.vitalsMonitorUpdateFrequency?.periodInMs,
                    useTracing = useTracing,
                    tracerApi = tracerApi?.name,
                    tracerApiVersion = openTelemetryApiVersion,
                    trackNetworkRequests = trackNetworkRequests,
                    sessionReplaySampleRate = sessionReplaySampleRate,
                    imagePrivacyLevel = sessionReplayImagePrivacy,
                    touchPrivacyLevel = sessionReplayTouchPrivacy,
                    textAndInputPrivacyLevel = sessionReplayTextAndInputPrivacy,
                    startRecordingImmediately = startRecordingImmediately,
                    batchProcessingLevel = event.batchProcessingLevel.toLong(),
                    isMainProcess = flashcatContext.processInfo.isMainProcess,
                    invTimeThresholdMs = invTimeBasedThreshold,
                    tnsTimeThresholdMs = tnsTimeBasedThreshold,
                    numberOfDisplays = flashcatContext.deviceInfo.numberOfDisplays?.toLong(),
                    traceSampleRate = okhttpInterceptorSampleRate?.toLong(),
                    selectedTracingPropagators = tracingHeaderTypes?.toSelectedTracingPropagators()
                )
            )
        )
    }

    private fun createApiUsageEvent(
        flashcatContext: FlashcatContext,
        timestamp: Long,
        event: InternalTelemetryEvent.ApiUsage,
        effectiveSampleRate: Float
    ): TelemetryUsageEvent {
        val rumContext = flashcatContext.rumContext()
        val resolvedAdditionalProperties = event.additionalProperties
            .cleanUpInternalAttributes()
        val usage = when (event) {
            is InternalTelemetryEvent.ApiUsage.AddOperationStepVital -> {
                TelemetryUsageEvent.Usage.AddOperationStepVital(
                    actionType = when (event.actionType) {
                        InternalTelemetryEvent.ApiUsage.AddOperationStepVital.ActionType.START -> ActionType.START
                        InternalTelemetryEvent.ApiUsage.AddOperationStepVital.ActionType.SUCCEED -> ActionType.SUCCEED
                        InternalTelemetryEvent.ApiUsage.AddOperationStepVital.ActionType.FAIL -> ActionType.FAIL
                    }
                )
            }
            is InternalTelemetryEvent.ApiUsage.AddViewLoadingTime -> {
                TelemetryUsageEvent.Usage.AddViewLoadingTime(
                    overwritten = event.overwrite,
                    noView = event.noView,
                    noActiveView = event.noActiveView
                )
            }
        }

        return TelemetryUsageEvent(
            dd = TelemetryUsageEvent.Dd(),
            date = timestamp,
            source = TelemetryUsageEvent.Source.tryFromSource(
                flashcatContext.source,
                sdkCore.internalLogger
            ) ?: TelemetryUsageEvent.Source.ANDROID,
            service = TELEMETRY_SERVICE_NAME,
            version = flashcatContext.sdkVersion,
            application = TelemetryUsageEvent.Application(rumContext.applicationId),
            session = TelemetryUsageEvent.Session(rumContext.sessionId),
            view = rumContext.viewId?.let { TelemetryUsageEvent.View(it) },
            action = rumContext.actionId?.let { TelemetryUsageEvent.Action(it) },
            effectiveSampleRate = effectiveSampleRate,
            telemetry = TelemetryUsageEvent.Telemetry(
                additionalProperties = resolvedAdditionalProperties,
                device = TelemetryUsageEvent.Device(
                    architecture = flashcatContext.deviceInfo.architecture,
                    brand = flashcatContext.deviceInfo.deviceBrand,
                    model = flashcatContext.deviceInfo.deviceModel
                ),
                os = TelemetryUsageEvent.Os(
                    build = flashcatContext.deviceInfo.deviceBuildId,
                    version = flashcatContext.deviceInfo.osVersion,
                    name = flashcatContext.deviceInfo.osName
                ),
                usage = usage
            )
        )
    }

    private fun isGlobalTracerRegistered(): Boolean {
        // We don't reference com.datadog.android.trace from RUM directly, so using reflection for this.
        // Would be nice to add the test with the flavor which is has no com.datadog.android.trace and test
        // for obfuscation enabled case.
        return try {
            val globalDatadogTracer =
                Class.forName("com.datadog.android.trace.GlobalDatadogTracer")
            return try {
                val holderInstance = globalDatadogTracer.getDeclaredField("INSTANCE").get(null)
                globalDatadogTracer.getDeclaredMethod("getOrNull").invoke(holderInstance) != null
            } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
                sdkCore.internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.TELEMETRY,
                    {
                        "GlobalDatadogTracer class exists in the runtime classpath, " +
                            "but there is an error invoking isRegistered method"
                    },
                    t
                )
                false
            }
        } catch (@Suppress("SwallowedException", "TooGenericExceptionCaught") t: Throwable) {
            // traces dependency is optional, so it is ok to not have such class
            // it can be also the case that our Proguard rule didn't work and class name is obfuscated
            false
        }
    }

    private fun isOpenTelemetryRegistered(traceContext: Map<String, Any?>): Boolean {
        return traceContext[IS_OPENTELEMETRY_ENABLED_CONTEXT_KEY] as? Boolean ?: false
    }

    private fun resolveTracerApi(traceContext: Map<String, Any?>): TracerApi? {
        return when {
            isOpenTelemetryRegistered(traceContext) -> TracerApi.OpenTelemetry
            isGlobalTracerRegistered() -> TracerApi.OpenTracing
            else -> null
        }
    }

    private fun resolveOpenTelemetryApiVersion(tracerApi: TracerApi?, traceContext: Map<String, Any?>): String? {
        return if (tracerApi == TracerApi.OpenTelemetry) {
            traceContext[OPENTELEMETRY_API_VERSION_CONTEXT_KEY] as? String
        } else {
            null
        }
    }

    private fun FlashcatContext.rumContext(): RumContext {
        val rumContext = featuresContext[Feature.RUM_FEATURE_NAME].orEmpty()
        return RumContext.fromFeatureContext(rumContext)
    }

    private fun computeEffectiveSampleRate(
        properties: Map<String, Any?>? = null,
        eventSpecificSamplingRate: Float? = null
    ): Float {
        val telemetrySampleRate = rumConfig?.telemetrySampleRate?.percent() ?: return 0f

        val creatingSamplingRate = properties
            ?.getFloat(LocalAttribute.Key.CREATION_SAMPLING_RATE)
            ?.percent() ?: 1.0

        val reportingSamplingRate = properties
            ?.getFloat(LocalAttribute.Key.REPORTING_SAMPLING_RATE)
            ?.percent() ?: 1.0

        val eventSamplingRate = eventSpecificSamplingRate?.percent() ?: 1.0

        val effectiveSampleRate = telemetrySampleRate * creatingSamplingRate * reportingSamplingRate * eventSamplingRate

        return (effectiveSampleRate * HUNDRED).toFloat()
    }

    private fun Map<String, Any?>.getFloat(key: LocalAttribute.Key) = get(key.toString()) as? Float

    private fun Map<String, Any?>.cleanUpInternalAttributes() = toMutableMap().apply {
        LocalAttribute.Key.values().forEach { key -> remove(key.toString()) }
    }

    // endregion

    internal enum class TracerApi {
        OpenTelemetry,
        OpenTracing
    }

    companion object {
        const val MAX_EVENTS_PER_SESSION = 100
        const val DEFAULT_CONFIGURATION_SAMPLE_RATE = 20f
        const val ALREADY_SEEN_EVENT_MESSAGE =
            "Already seen telemetry event with identity=%s, rejecting."
        const val MAX_EVENT_NUMBER_REACHED_MESSAGE =
            "Max number of telemetry events per session reached, rejecting."
        const val TELEMETRY_SERVICE_NAME = "dd-sdk-android"
        internal const val IS_OPENTELEMETRY_ENABLED_CONTEXT_KEY = "is_opentelemetry_enabled"
        internal const val OPENTELEMETRY_API_VERSION_CONTEXT_KEY = "opentelemetry_api_version"
        internal const val SESSION_REPLAY_SAMPLE_RATE_KEY = "session_replay_sample_rate"
        internal const val SESSION_REPLAY_TEXT_AND_INPUT_PRIVACY_KEY = "session_replay_text_and_input_privacy"
        internal const val SESSION_REPLAY_IMAGE_PRIVACY_KEY = "session_replay_image_privacy"
        internal const val SESSION_REPLAY_TOUCH_PRIVACY_KEY = "session_replay_touch_privacy"
        internal const val SESSION_REPLAY_START_IMMEDIATE_RECORDING_KEY =
            "session_replay_start_immediate_recording"

        internal const val OKHTTP_INTERCEPTOR_SAMPLE_RATE = "okhttp_interceptor_sample_rate"
        internal const val OKHTTP_INTERCEPTOR_HEADER_TYPES = "okhttp_interceptor_header_types"
    }
}
