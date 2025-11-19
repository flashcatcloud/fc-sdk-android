/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.rum.internal.domain.scope

import androidx.annotation.WorkerThread
import com.flashcat.rum.api.context.FlashcatContext
import com.flashcat.rum.api.feature.EventWriteScope
import com.flashcat.rum.api.storage.DataWriter
import com.flashcat.rum.core.InternalSdkCore
import com.flashcat.rum.rum.RumActionType
import com.flashcat.rum.rum.RumSessionType
import com.flashcat.rum.rum.internal.FeaturesContextResolver
import com.flashcat.rum.rum.internal.domain.RumContext
import com.flashcat.rum.rum.internal.domain.Time
import com.flashcat.rum.rum.internal.monitor.StorageEvent
import com.flashcat.rum.rum.internal.toAction
import com.flashcat.rum.rum.internal.utils.buildDDTagsString
import com.flashcat.rum.rum.internal.utils.hasUserData
import com.flashcat.rum.rum.internal.utils.newRumEventWriteOperation
import com.flashcat.rum.rum.model.ActionEvent
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.max

internal class RumActionScope(
    override val parentScope: RumScope,
    private val sdkCore: InternalSdkCore,
    val waitForStop: Boolean,
    eventTime: Time,
    initialType: RumActionType,
    initialName: String,
    initialAttributes: Map<String, Any?>,
    serverTimeOffsetInMs: Long,
    inactivityThresholdMs: Long = ACTION_INACTIVITY_MS,
    maxDurationMs: Long = ACTION_MAX_DURATION_MS,
    private val featuresContextResolver: FeaturesContextResolver = FeaturesContextResolver(),
    private val trackFrustrations: Boolean,
    internal val sampleRate: Float,
    private val rumSessionTypeOverride: RumSessionType?
) : RumScope {

    private val inactivityThresholdNs = TimeUnit.MILLISECONDS.toNanos(inactivityThresholdMs)
    private val maxDurationNs = TimeUnit.MILLISECONDS.toNanos(maxDurationMs)

    internal val eventTimestamp = eventTime.timestamp + serverTimeOffsetInMs
    internal val actionId: String = UUID.randomUUID().toString()
    internal var type: RumActionType = initialType
    internal var name: String = initialName
    internal val startedNanos: Long = eventTime.nanoTime
    private var stoppedNanos: Long = startedNanos
    private var lastInteractionNanos: Long = startedNanos
    private val networkInfo = sdkCore.networkInfo

    internal val actionAttributes: MutableMap<String, Any?> = initialAttributes.toMutableMap()

    private val ongoingResourceKeys = mutableListOf<WeakReference<Any>>()

    internal var resourceCount: Long = 0
    internal var errorCount: Long = 0
    internal var crashCount: Long = 0
    internal var longTaskCount: Long = 0

    private var sent = false
    internal var stopped = false

    // endregion

    @WorkerThread
    override fun handleEvent(
        event: RumRawEvent,
        flashcatContext: FlashcatContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>
    ): RumScope? {
        val now = event.eventTime.nanoTime
        val isInactive = now - lastInteractionNanos > inactivityThresholdNs
        val isLongDuration = now - startedNanos > maxDurationNs
        ongoingResourceKeys.removeAll { it.get() == null }
        val isOngoing = waitForStop && !stopped
        val shouldStop = isInactive && ongoingResourceKeys.isEmpty() && !isOngoing

        when {
            shouldStop -> sendAction(lastInteractionNanos, flashcatContext, writeScope, writer)
            isLongDuration -> sendAction(now, flashcatContext, writeScope, writer)
            event is RumRawEvent.SendCustomActionNow -> sendAction(
                lastInteractionNanos,
                flashcatContext,
                writeScope,
                writer
            )
            event is RumRawEvent.StartView -> onStartView(now, flashcatContext, writeScope, writer)
            event is RumRawEvent.StopView -> onStopView(now, flashcatContext, writeScope, writer)
            event is RumRawEvent.StopSession -> onStopSession(now, flashcatContext, writeScope, writer)
            event is RumRawEvent.StopAction -> onStopAction(event, now)
            event is RumRawEvent.StartResource -> onStartResource(event, now)
            event is RumRawEvent.StopResource -> onStopResource(event, now)
            event is RumRawEvent.AddError -> onError(event, now, flashcatContext, writeScope, writer)
            event is RumRawEvent.StopResourceWithError -> onResourceError(event.key, now)
            event is RumRawEvent.StopResourceWithStackTrace -> onResourceError(event.key, now)
            event is RumRawEvent.AddLongTask -> onLongTask(now)
        }

        return if (sent) null else this
    }

    override fun getRumContext(): RumContext {
        return parentScope.getRumContext()
    }

    override fun getCustomAttributes(): Map<String, Any?> {
        return parentScope.getCustomAttributes() + actionAttributes
    }

    override fun isActive(): Boolean {
        return !stopped
    }

    // endregion

    // region Internal

    @WorkerThread
    private fun onStartView(
        now: Long,
        flashcatContext: FlashcatContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>
    ) {
        // another view starts, complete this action
        ongoingResourceKeys.clear()
        sendAction(now, flashcatContext, writeScope, writer)
    }

    @WorkerThread
    private fun onStopView(
        now: Long,
        flashcatContext: FlashcatContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>
    ) {
        ongoingResourceKeys.clear()
        sendAction(now, flashcatContext, writeScope, writer)
    }

    @WorkerThread
    private fun onStopSession(
        now: Long,
        flashcatContext: FlashcatContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>
    ) {
        ongoingResourceKeys.clear()
        sendAction(now, flashcatContext, writeScope, writer)
    }

    private fun onStopAction(
        event: RumRawEvent.StopAction,
        now: Long
    ) {
        event.type?.let { type = it }
        event.name?.let { name = it }
        actionAttributes.putAll(event.attributes)
        stopped = true
        stoppedNanos = now
        lastInteractionNanos = now
    }

    private fun onStartResource(
        event: RumRawEvent.StartResource,
        now: Long
    ) {
        lastInteractionNanos = now
        resourceCount++
        ongoingResourceKeys.add(WeakReference(event.key))
    }

    private fun onStopResource(
        event: RumRawEvent.StopResource,
        now: Long
    ) {
        val keyRef = ongoingResourceKeys.firstOrNull { it.get() == event.key }
        if (keyRef != null) {
            ongoingResourceKeys.remove(keyRef)
            lastInteractionNanos = now
        }
    }

    @WorkerThread
    private fun onError(
        event: RumRawEvent.AddError,
        now: Long,
        flashcatContext: FlashcatContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>
    ) {
        lastInteractionNanos = now
        errorCount++

        if (event.isFatal) {
            crashCount++
            sendAction(now, flashcatContext, writeScope, writer)
        }
    }

    private fun onResourceError(eventKey: Any, now: Long) {
        val keyRef = ongoingResourceKeys.firstOrNull { it.get() == eventKey }
        if (keyRef != null) {
            ongoingResourceKeys.remove(keyRef)
            lastInteractionNanos = now
            resourceCount--
            errorCount++
        }
    }

    private fun onLongTask(now: Long) {
        lastInteractionNanos = now
        longTaskCount++
    }

    @Suppress("LongMethod", "ComplexMethod")
    private fun sendAction(
        endNanos: Long,
        flashcatContext: FlashcatContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>
    ) {
        if (sent) return

        val actualType = type
        val rumContext = getRumContext()

        // make a copy so that closure captures at the state as of now
        // normally not needed, because it should be only single event for this scope, but
        // just in case
        val eventName = name
        val eventErrorCount = errorCount
        val eventCrashCount = crashCount
        val eventLongTaskCount = longTaskCount
        val eventResourceCount = resourceCount
        val loadingTime = max(endNanos - startedNanos, 1L)

        val syntheticsAttribute = if (
            rumContext.syntheticsTestId.isNullOrBlank() ||
            rumContext.syntheticsResultId.isNullOrBlank()
        ) {
            null
        } else {
            ActionEvent.Synthetics(
                testId = rumContext.syntheticsTestId,
                resultId = rumContext.syntheticsResultId
            )
        }

        val sessionType = when {
            rumSessionTypeOverride != null -> rumSessionTypeOverride.toAction()
            syntheticsAttribute == null -> ActionEvent.ActionEventSessionType.USER
            else -> ActionEvent.ActionEventSessionType.SYNTHETICS
        }

        val frustrations = mutableListOf<ActionEvent.Type>()
        if (trackFrustrations && eventErrorCount > 0 && actualType == RumActionType.TAP) {
            frustrations.add(ActionEvent.Type.ERROR_TAP)
        }

        sdkCore.newRumEventWriteOperation(flashcatContext, writeScope, writer) {
            val user = flashcatContext.userInfo
            val hasReplay = featuresContextResolver.resolveViewHasReplay(
                flashcatContext,
                rumContext.viewId.orEmpty()
            )

            ActionEvent(
                date = eventTimestamp,
                action = ActionEvent.ActionEventAction(
                    type = actualType.toSchemaType(),
                    id = actionId,
                    target = ActionEvent.ActionEventActionTarget(eventName),
                    error = ActionEvent.Error(eventErrorCount),
                    crash = ActionEvent.Crash(eventCrashCount),
                    longTask = ActionEvent.LongTask(eventLongTaskCount),
                    resource = ActionEvent.Resource(eventResourceCount),
                    loadingTime = loadingTime,
                    frustration = if (frustrations.isNotEmpty()) {
                        ActionEvent.Frustration(frustrations)
                    } else {
                        null
                    }
                ),
                view = ActionEvent.ActionEventView(
                    id = rumContext.viewId.orEmpty(),
                    name = rumContext.viewName,
                    url = rumContext.viewUrl.orEmpty()
                ),
                application = ActionEvent.Application(
                    id = rumContext.applicationId,
                    currentLocale = flashcatContext.deviceInfo.localeInfo.currentLocale
                ),
                session = ActionEvent.ActionEventSession(
                    id = rumContext.sessionId,
                    type = sessionType,
                    hasReplay = hasReplay
                ),
                synthetics = syntheticsAttribute,
                source = ActionEvent.ActionEventSource.tryFromSource(
                    flashcatContext.source,
                    sdkCore.internalLogger
                ),
                usr = if (user.hasUserData()) {
                    ActionEvent.Usr(
                        id = user.id,
                        name = user.name,
                        email = user.email,
                        anonymousId = user.anonymousId,
                        additionalProperties = user.additionalProperties.toMutableMap()
                    )
                } else {
                    null
                },
                account = flashcatContext.accountInfo?.let {
                    ActionEvent.Account(
                        id = it.id,
                        name = it.name,
                        additionalProperties = it.extraInfo.toMutableMap()
                    )
                },
                os = ActionEvent.Os(
                    name = flashcatContext.deviceInfo.osName,
                    version = flashcatContext.deviceInfo.osVersion,
                    versionMajor = flashcatContext.deviceInfo.osMajorVersion
                ),
                device = ActionEvent.Device(
                    type = flashcatContext.deviceInfo.deviceType.toActionSchemaType(),
                    name = flashcatContext.deviceInfo.deviceName,
                    model = flashcatContext.deviceInfo.deviceModel,
                    brand = flashcatContext.deviceInfo.deviceBrand,
                    architecture = flashcatContext.deviceInfo.architecture,
                    locales = flashcatContext.deviceInfo.localeInfo.locales,
                    timeZone = flashcatContext.deviceInfo.localeInfo.timeZone
                ),
                context = ActionEvent.Context(additionalProperties = getCustomAttributes().toMutableMap()),
                dd = ActionEvent.Dd(
                    session = ActionEvent.DdSession(
                        sessionPrecondition = rumContext.sessionStartReason.toActionSessionPrecondition()
                    ),
                    configuration = ActionEvent.Configuration(sessionSampleRate = sampleRate)
                ),
                connectivity = networkInfo.toActionConnectivity(),
                service = flashcatContext.service,
                version = flashcatContext.version,
                ddtags = buildDDTagsString(flashcatContext)
            )
        }
            .apply {
                val storageEvent =
                    StorageEvent.Action(frustrations.size, actualType.toSchemaType(), stoppedNanos)
                onError {
                    it.eventDropped(rumContext.viewId.orEmpty(), storageEvent)
                }
                onSuccess {
                    it.eventSent(rumContext.viewId.orEmpty(), storageEvent)
                }
            }
            .submit()

        sent = true
    }

    // endregion

    companion object {
        internal const val ACTION_INACTIVITY_MS = 100L
        internal const val ACTION_MAX_DURATION_MS = 5000L

        @Suppress("LongParameterList")
        fun fromEvent(
            parentScope: RumScope,
            sdkCore: InternalSdkCore,
            event: RumRawEvent.StartAction,
            timestampOffset: Long,
            featuresContextResolver: FeaturesContextResolver,
            trackFrustrations: Boolean,
            sampleRate: Float,
            rumSessionTypeOverride: RumSessionType?
        ): RumScope {
            return RumActionScope(
                parentScope = parentScope,
                sdkCore = sdkCore,
                waitForStop = event.waitForStop,
                eventTime = event.eventTime,
                initialType = event.type,
                initialName = event.name,
                initialAttributes = event.attributes,
                serverTimeOffsetInMs = timestampOffset,
                featuresContextResolver = featuresContextResolver,
                trackFrustrations = trackFrustrations,
                sampleRate = sampleRate,
                rumSessionTypeOverride = rumSessionTypeOverride
            )
        }
    }
}
