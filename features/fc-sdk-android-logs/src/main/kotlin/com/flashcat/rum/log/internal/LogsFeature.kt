/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.log.internal

import android.content.Context
import android.util.Log
import androidx.annotation.AnyThread
import com.flashcat.rum.api.InternalLogger
import com.flashcat.rum.api.feature.Feature
import com.flashcat.rum.api.feature.FeatureEventReceiver
import com.flashcat.rum.api.feature.FeatureSdkCore
import com.flashcat.rum.api.feature.StorageBackedFeature
import com.flashcat.rum.api.net.RequestFactory
import com.flashcat.rum.api.storage.DataWriter
import com.flashcat.rum.api.storage.EventType
import com.flashcat.rum.api.storage.FeatureStorageConfiguration
import com.flashcat.rum.api.storage.NoOpDataWriter
import com.flashcat.rum.event.EventMapper
import com.flashcat.rum.event.MapperSerializer
import com.flashcat.rum.internal.utils.NULL_MAP_VALUE
import com.flashcat.rum.log.internal.domain.DatadogLogGenerator
import com.flashcat.rum.log.internal.domain.event.LogEventMapperWrapper
import com.flashcat.rum.log.internal.domain.event.LogEventSerializer
import com.flashcat.rum.log.internal.net.LogsRequestFactory
import com.flashcat.rum.log.internal.storage.LogsDataWriter
import com.flashcat.rum.log.model.LogEvent
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Logs feature class, which needs to be registered with Flashcat SDK instance.
 */
internal class LogsFeature(
    private val sdkCore: FeatureSdkCore,
    customEndpointUrl: String?,
    internal val eventMapper: EventMapper<LogEvent>
) : StorageBackedFeature, FeatureEventReceiver {

    internal var dataWriter: DataWriter<LogEvent> = NoOpDataWriter()
    private val initialized = AtomicBoolean(false)
    internal var packageName = ""
    private val logGenerator = DatadogLogGenerator()
    private val attributes = ConcurrentHashMap<String, Any?>()

    // region Context Information (attributes)
    /**
     * Add a custom attribute to all logs sent by any logger created from this feature.
     *
     * Values can be nested up to 10 levels deep. Keys
     * using more than 10 levels will be sanitized by SDK.
     *
     * @param key the key for this attribute
     * @param value the attribute value
     */
    internal fun addAttribute(key: String, value: Any?) {
        if (value == null) {
            attributes[key] = NULL_MAP_VALUE
        } else {
            attributes[key] = value
        }
    }

    /**
     * Remove a custom attribute from all future logs sent by any logger created from this feature.
     * Previous logs won't lose the attribute value associated with this key if they were created
     * prior to this call.
     * @param key the key of the attribute to remove
     */
    internal fun removeAttribute(key: String) {
        @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
        attributes.remove(key)
    }

    internal fun getAttributes(): Map<String, Any?> {
        @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
        return attributes.toMap()
    }

    // endregion

    // region Feature

    override val name: String = Feature.LOGS_FEATURE_NAME

    override fun onInitialize(appContext: Context) {
        sdkCore.setEventReceiver(name, this)

        packageName = appContext.packageName

        dataWriter = createDataWriter(eventMapper)
        initialized.set(true)
    }

    override val requestFactory: RequestFactory by lazy {
        LogsRequestFactory(
            customEndpointUrl,
            sdkCore.internalLogger
        )
    }

    override val storageConfiguration: FeatureStorageConfiguration =
        FeatureStorageConfiguration.DEFAULT

    override fun onStop() {
        sdkCore.removeEventReceiver(name)
        dataWriter = NoOpDataWriter()
        packageName = ""
        initialized.set(false)
        @Suppress("UnsafeThirdPartyFunctionCall")
        attributes.clear()
    }

    // endregion

    // region FeatureEventReceiver

    @AnyThread
    override fun onReceive(event: Any) {
        if (event !is Map<*, *>) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { UNSUPPORTED_EVENT_TYPE.format(Locale.US, event::class.java.canonicalName) }
            )
            return
        }

        if (event[TYPE_EVENT_KEY] == "span_log") {
            sendSpanLog(event)
        } else {
            sdkCore.internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { UNKNOWN_EVENT_TYPE_PROPERTY_VALUE.format(Locale.US, event[TYPE_EVENT_KEY]) }
            )
        }
    }

    // endregion

    // region Internal

    private fun createDataWriter(
        eventMapper: EventMapper<LogEvent>
    ): DataWriter<LogEvent> {
        return LogsDataWriter(
            serializer = MapperSerializer(
                LogEventMapperWrapper(eventMapper, sdkCore.internalLogger),
                LogEventSerializer(sdkCore.internalLogger)
            ),
            internalLogger = sdkCore.internalLogger
        )
    }

    private fun sendSpanLog(data: Map<*, *>) {
        val timestamp = data[TIMESTAMP_EVENT_KEY] as? Long
        val message = data[MESSAGE_EVENT_KEY] as? String
        val loggerName = data[LOGGER_NAME_EVENT_KEY] as? String
        val attributes = (data[ATTRIBUTES_EVENT_KEY] as? Map<*, *>)
            ?.filterKeys { it is String }
            ?.mapKeys { it.key as String }

        val logStatus = data[LOG_STATUS_EVENT_KEY] as? Int ?: Log.VERBOSE

        @Suppress("ComplexCondition")
        if (loggerName == null || message == null || attributes == null || timestamp == null) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { SPAN_LOG_EVENT_MISSING_MANDATORY_FIELDS_WARNING }
            )
            return
        }

        sdkCore.getFeature(name)
            ?.withWriteContext(
                withFeatureContexts = setOf(Feature.RUM_FEATURE_NAME)
            ) { flashcatContext, writeScope ->
                val log = logGenerator.generateLog(
                    logStatus,
                    flashcatContext = flashcatContext,
                    attachNetworkInfo = true,
                    loggerName = loggerName,
                    message = message,
                    throwable = null,
                    attributes = attributes,
                    timestamp = timestamp,
                    // false, because span log event will already have the necessary attributes
                    bundleWithTraces = false,
                    bundleWithRum = true,
                    threadName = Thread.currentThread().name,
                    tags = emptySet()
                )

                writeScope {
                    dataWriter.write(it, log, EventType.DEFAULT)
                }
            }
    }

    // endregion

    internal companion object {

        private const val TYPE_EVENT_KEY = "type"
        private const val TIMESTAMP_EVENT_KEY = "timestamp"
        private const val LOGGER_NAME_EVENT_KEY = "loggerName"
        private const val ATTRIBUTES_EVENT_KEY = "attributes"
        private const val MESSAGE_EVENT_KEY = "message"
        private const val LOG_STATUS_EVENT_KEY = "logStatus"

        internal const val UNSUPPORTED_EVENT_TYPE =
            "Logs feature receive an event of unsupported type=%s."
        internal const val UNKNOWN_EVENT_TYPE_PROPERTY_VALUE =
            "Logs feature received an event with unknown value of \"type\" property=%s."
        internal const val NDK_CRASH_EVENT_MISSING_MANDATORY_FIELDS_WARNING =
            "Logs feature received a NDK crash event where" +
                " one or more mandatory (loggerName, message, timestamp, attributes)" +
                " fields are either missing or have wrong type."
        internal const val SPAN_LOG_EVENT_MISSING_MANDATORY_FIELDS_WARNING =
            "Logs feature received a Span log event where" +
                " one or more mandatory (loggerName, message, timestamp, attributes)" +
                " fields are either missing or have wrong type."

        internal const val MAX_WRITE_WAIT_TIMEOUT_MS = 500L
    }
}
