/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.log.internal.domain

import com.flashcat.rum.api.context.AccountInfo
import com.flashcat.rum.api.context.FlashcatContext
import com.flashcat.rum.api.context.DeviceInfo
import com.flashcat.rum.api.context.DeviceType
import com.flashcat.rum.api.context.NetworkInfo
import com.flashcat.rum.api.context.UserInfo
import com.flashcat.rum.api.feature.Feature
import com.flashcat.rum.core.feature.event.ThreadDump
import com.flashcat.rum.log.LogAttributes
import com.flashcat.rum.log.internal.utils.buildLogDateFormat
import com.flashcat.rum.log.model.LogEvent
import java.util.Date

@Suppress("TooManyFunctions")
internal class DatadogLogGenerator(
    /**
     * Custom service name. If not provided, value will be taken from [FlashcatContext].
     */
    internal val serviceName: String? = null
) : LogGenerator {

    private val simpleDateFormat = buildLogDateFormat()

    @Suppress("LongParameterList")
    override fun generateLog(
        level: Int,
        message: String,
        throwable: Throwable?,
        attributes: Map<String, Any?>,
        tags: Set<String>,
        timestamp: Long,
        threadName: String,
        flashcatContext: FlashcatContext,
        attachNetworkInfo: Boolean,
        loggerName: String,
        bundleWithTraces: Boolean,
        bundleWithRum: Boolean,
        userInfo: UserInfo?,
        accountInfo: AccountInfo?,
        networkInfo: NetworkInfo?,
        threads: List<ThreadDump>
    ): LogEvent {
        val mutableAttributes = attributes.toMutableMap()
        val error = throwable?.let {
            val fingerprint = mutableAttributes.remove(LogAttributes.ERROR_FINGERPRINT) as? String
            val kind = it.javaClass.canonicalName ?: it.javaClass.simpleName
            LogEvent.Error(
                kind = kind,
                stack = it.stackTraceToString(),
                message = it.message,
                fingerprint = fingerprint,
                threads = threads.map { thread ->
                    LogEvent.Thread(
                        name = thread.name,
                        crashed = thread.crashed,
                        stack = thread.stack,
                        state = thread.state
                    )
                }.ifEmpty { null }
            )
        }
        return internalGenerateLog(
            level,
            message,
            error,
            mutableAttributes,
            tags,
            timestamp,
            threadName,
            flashcatContext,
            attachNetworkInfo,
            loggerName,
            bundleWithTraces,
            bundleWithRum,
            userInfo,
            accountInfo,
            networkInfo
        )
    }

    override fun generateLog(
        level: Int,
        message: String,
        errorKind: String?,
        errorMessage: String?,
        errorStack: String?,
        attributes: Map<String, Any?>,
        tags: Set<String>,
        timestamp: Long,
        threadName: String,
        flashcatContext: FlashcatContext,
        attachNetworkInfo: Boolean,
        loggerName: String,
        bundleWithTraces: Boolean,
        bundleWithRum: Boolean,
        userInfo: UserInfo?,
        accountInfo: AccountInfo?,
        networkInfo: NetworkInfo?
    ): LogEvent {
        val mutableAttributes = attributes.toMutableMap()
        val error = if (errorKind != null || errorMessage != null || errorStack != null) {
            val sourceType = mutableAttributes.remove(LogAttributes.SOURCE_TYPE) as? String
            val fingerprint = mutableAttributes.remove(LogAttributes.ERROR_FINGERPRINT) as? String
            LogEvent.Error(
                kind = errorKind,
                message = errorMessage,
                stack = errorStack,
                fingerprint = fingerprint,
                sourceType = sourceType
            )
        } else {
            null
        }
        return internalGenerateLog(
            level,
            message,
            error,
            mutableAttributes,
            tags,
            timestamp,
            threadName,
            flashcatContext,
            attachNetworkInfo,
            loggerName,
            bundleWithTraces,
            bundleWithRum,
            userInfo,
            accountInfo,
            networkInfo
        )
    }

    // region Internal

    @Suppress("LongParameterList")
    private fun internalGenerateLog(
        level: Int,
        message: String,
        error: LogEvent.Error?,
        attributes: Map<String, Any?>,
        tags: Set<String>,
        timestamp: Long,
        threadName: String,
        flashcatContext: FlashcatContext,
        attachNetworkInfo: Boolean,
        loggerName: String,
        bundleWithTraces: Boolean,
        bundleWithRum: Boolean,
        userInfo: UserInfo?,
        accountInfo: AccountInfo?,
        networkInfo: NetworkInfo?
    ): LogEvent {
        val resolvedTimestamp = timestamp + flashcatContext.time.serverTimeOffsetMs
        val combinedAttributes = resolveAttributes(
            flashcatContext,
            attributes,
            bundleWithTraces,
            threadName,
            bundleWithRum
        )
        val formattedDate = synchronized(simpleDateFormat) {
            @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
            simpleDateFormat.format(Date(resolvedTimestamp))
        }
        val deviceInfo = flashcatContext.deviceInfo
        val combinedTags = resolveTags(flashcatContext, tags)
        val usr = resolveUserInfo(flashcatContext, userInfo)
        val account = resolveAccountInfo(flashcatContext, accountInfo)
        val network = if (networkInfo != null || attachNetworkInfo) {
            resolveNetworkInfo(flashcatContext, networkInfo)
        } else {
            null
        }
        val loggerInfo = LogEvent.Logger(
            name = loggerName,
            threadName = threadName,
            version = flashcatContext.sdkVersion
        )
        return LogEvent(
            service = serviceName ?: flashcatContext.service,
            status = resolveLogLevelStatus(level),
            message = message,
            date = formattedDate,
            // TODO RUM-3832 If NDK crash, the it should be a value from previous build
            //  (or whatever distinguishes debug symbols for native libs)
            buildId = flashcatContext.appBuildId,
            error = error,
            logger = loggerInfo,
            dd = LogEvent.Dd(
                device = LogEvent.DdDevice(
                    architecture = deviceInfo.architecture
                )
            ),
            usr = usr,
            account = account,
            network = network,
            ddtags = combinedTags.joinToString(separator = ","),
            additionalProperties = combinedAttributes,
            os = resolveOsInfo(deviceInfo),
            device = resolveDeviceInfo(deviceInfo)
        )
    }

    private fun resolveOsInfo(deviceInfo: DeviceInfo) = LogEvent.Os(
        name = deviceInfo.osName,
        version = deviceInfo.osVersion,
        versionMajor = deviceInfo.osMajorVersion
    )

    private fun resolveDeviceInfo(deviceInfo: DeviceInfo) = LogEvent.LogEventDevice(
        type = resolveDeviceType(deviceInfo.deviceType),
        name = deviceInfo.deviceName,
        model = deviceInfo.deviceModel,
        brand = deviceInfo.deviceBrand,
        architecture = deviceInfo.architecture
    )

    private fun resolveDeviceType(deviceType: DeviceType): LogEvent.Type = when (deviceType) {
        DeviceType.MOBILE -> LogEvent.Type.MOBILE
        DeviceType.TABLET -> LogEvent.Type.TABLET
        DeviceType.TV -> LogEvent.Type.TV
        DeviceType.DESKTOP -> LogEvent.Type.DESKTOP
        DeviceType.GAMING_CONSOLE -> LogEvent.Type.GAMING_CONSOLE
        DeviceType.BOT -> LogEvent.Type.BOT
        DeviceType.OTHER -> LogEvent.Type.OTHER
    }

    private fun envTag(flashcatContext: FlashcatContext): String? {
        val envName = flashcatContext.env
        return if (envName.isNotEmpty()) {
            "${LogAttributes.ENV}:$envName"
        } else {
            null
        }
    }

    private fun appVersionTag(flashcatContext: FlashcatContext): String? {
        val appVersion = flashcatContext.version
        return if (appVersion.isNotEmpty()) {
            "${LogAttributes.APPLICATION_VERSION}:$appVersion"
        } else {
            null
        }
    }

    private fun variantTag(flashcatContext: FlashcatContext): String? {
        val variant = flashcatContext.variant
        return if (variant.isNotEmpty()) {
            "${LogAttributes.VARIANT}:$variant"
        } else {
            null
        }
    }

    private fun serviceTag(flashcatContext: FlashcatContext): String? {
        val service = flashcatContext.service
        return if (service.isNotEmpty()) {
            "${LogAttributes.SERVICE}:$service"
        } else {
            null
        }
    }

    private fun resolveNetworkInfo(
        flashcatContext: FlashcatContext,
        networkInfo: NetworkInfo?
    ): LogEvent.Network {
        return with(networkInfo ?: flashcatContext.networkInfo) {
            LogEvent.Network(
                LogEvent.Client(
                    simCarrier = resolveSimCarrier(this),
                    signalStrength = strength?.toString(),
                    downlinkKbps = downKbps?.toString(),
                    uplinkKbps = upKbps?.toString(),
                    connectivity = connectivity.toString()
                )
            )
        }
    }

    private fun resolveUserInfo(flashcatContext: FlashcatContext, userInfo: UserInfo?): LogEvent.Usr {
        return with(userInfo ?: flashcatContext.userInfo) {
            LogEvent.Usr(
                anonymousId = anonymousId,
                name = name,
                email = email,
                id = id,
                additionalProperties = additionalProperties.toMutableMap()
            )
        }
    }

    private fun resolveAccountInfo(
        flashcatContext: FlashcatContext,
        accountInfo: AccountInfo?
    ): LogEvent.Account? {
        return (accountInfo ?: flashcatContext.accountInfo)?.let {
            LogEvent.Account(
                id = it.id,
                name = it.name,
                additionalProperties = it.extraInfo.toMutableMap()
            )
        }
    }

    private fun resolveTags(
        flashcatContext: FlashcatContext,
        tags: Set<String>
    ): MutableSet<String> {
        val combinedTags = mutableSetOf<String>().apply { addAll(tags) }
        envTag(flashcatContext)?.let {
            combinedTags.add(it)
        }
        appVersionTag(flashcatContext)?.let {
            combinedTags.add(it)
        }
        variantTag(flashcatContext)?.let {
            combinedTags.add(it)
        }
        serviceTag(flashcatContext)?.let {
            combinedTags.add(it)
        }

        return combinedTags
    }

    private fun resolveAttributes(
        flashcatContext: FlashcatContext,
        attributes: Map<String, Any?>,
        bundleWithTraces: Boolean,
        threadName: String,
        bundleWithRum: Boolean
    ): MutableMap<String, Any?> {
        val combinedAttributes = mutableMapOf<String, Any?>().apply { putAll(attributes) }
        if (bundleWithTraces) {
            flashcatContext.featuresContext[Feature.TRACING_FEATURE_NAME]?.let {
                val threadLocalContext = it["context@$threadName"] as? Map<*, *>
                if (threadLocalContext != null) {
                    combinedAttributes[LogAttributes.DD_TRACE_ID] = threadLocalContext["trace_id"]
                    combinedAttributes[LogAttributes.DD_SPAN_ID] = threadLocalContext["span_id"]
                }
            }
        }
        if (bundleWithRum) {
            flashcatContext.featuresContext[Feature.RUM_FEATURE_NAME]?.let {
                combinedAttributes[LogAttributes.RUM_APPLICATION_ID] = it["application_id"]
                combinedAttributes[LogAttributes.RUM_SESSION_ID] = it["session_id"]
                combinedAttributes[LogAttributes.RUM_VIEW_ID] = it["view_id"]
                combinedAttributes[LogAttributes.RUM_ACTION_ID] = it["action_id"]
            }
        }
        return combinedAttributes
    }

    @Suppress("DEPRECATION")
    private fun resolveLogLevelStatus(level: Int): LogEvent.Status {
        return when (level) {
            android.util.Log.ASSERT -> LogEvent.Status.CRITICAL
            android.util.Log.ERROR -> LogEvent.Status.ERROR
            android.util.Log.WARN -> LogEvent.Status.WARN
            android.util.Log.INFO -> LogEvent.Status.INFO
            android.util.Log.DEBUG -> LogEvent.Status.DEBUG
            android.util.Log.VERBOSE -> LogEvent.Status.TRACE
            DatadogLogGenerator.CRASH -> LogEvent.Status.EMERGENCY
            else -> LogEvent.Status.DEBUG
        }
    }

    private fun resolveSimCarrier(networkInfo: NetworkInfo): LogEvent.SimCarrier? {
        return if (networkInfo.carrierId != null || networkInfo.carrierName != null) {
            LogEvent.SimCarrier(
                id = networkInfo.carrierId?.toString(),
                name = networkInfo.carrierName
            )
        } else {
            null
        }
    }

    // endregion

    companion object {
        internal const val ISO_8601 = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
        internal const val CRASH: Int = 9
    }
}
