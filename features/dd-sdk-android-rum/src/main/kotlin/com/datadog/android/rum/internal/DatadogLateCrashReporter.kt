/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal

import android.app.ApplicationExitInfo
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.feature.event.ThreadDump
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.rum.internal.anr.ANRDetectorRunnable
import com.datadog.android.rum.internal.anr.ANRException
import com.datadog.android.rum.internal.anr.AndroidTraceParser
import com.datadog.android.rum.internal.crash.CrashBeacon
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.RumDataWriter
import com.datadog.android.rum.internal.domain.event.RumEventDeserializer
import com.datadog.android.rum.internal.domain.scope.RumViewManagerScope
import com.datadog.android.rum.internal.domain.scope.toErrorSchemaType
import com.datadog.android.rum.internal.domain.scope.toViewSchemaType
import com.datadog.android.rum.internal.domain.scope.tryFromSource
import com.datadog.android.rum.internal.utils.buildDDTagsString
import com.datadog.android.rum.internal.utils.hasUserData
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.ViewEvent
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

internal class DatadogLateCrashReporter(
    private val sdkCore: InternalSdkCore,
    private val rumEventDeserializer: Deserializer<JsonObject, Any> = RumEventDeserializer(
        sdkCore.internalLogger
    ),
    private val androidTraceParser: AndroidTraceParser = AndroidTraceParser(sdkCore.internalLogger),
    /**
     * FLASHCAT FORK - draws the session a crash that belongs to no known session is reported in,
     * like a new session would be drawn now; null when that draw does not keep it.
     */
    private val drawNewSession: () -> NewSession? = {
        sdkCore.getFeature(Feature.RUM_FEATURE_NAME)?.unwrap<RumFeature>()?.drawSessionForLateCrash()
    }
) : LateCrashReporter {

    // region LateCrashEventHandler

    @Suppress("ComplexCondition")
    override fun handleNdkCrashEvent(
        event: Map<*, *>,
        rumWriter: DataWriter<Any>
    ) {
        val rumFeature = sdkCore.getFeature(Feature.RUM_FEATURE_NAME)

        if (rumFeature == null) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.USER,
                { INFO_RUM_FEATURE_NOT_REGISTERED }
            )
            return
        }

        val sourceType = event["sourceType"] as? String
        val timestamp = event["timestamp"] as? Long
        val timeSinceAppStartMs = event["timeSinceAppStartMs"] as? Long
        val signalName = event["signalName"] as? String
        val stacktrace = event["stacktrace"] as? String
        val errorLogMessage = event["message"] as? String
        // FLASHCAT FORK - the last view is no longer mandatory: a crash that belongs to no view
        // known here is reported in a session of its own, see [resolveTarget].
        val lastSlot = readSlot(event["lastViewEvent"] as? JsonObject)
        // FLASHCAT FORK - present when the crash sent a crash beacon: the report is counted under the
        // beacon's id, with its outcome.
        val crashId = event["crashId"] as? String
        val beacon = (event["beaconStatus"] as? String)?.let {
            ErrorEvent.Beacon(status = it, duration = event["beaconDuration"] as? Long)
        }
        // The date the beacon was sent under: the intake derives the launch day of both from their
        // dates, and this launch's clock offset may not be the crashed process's.
        val crashDate = event["crashDate"] as? Long

        if (timestamp == null || signalName == null || stacktrace == null || errorLogMessage == null) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { NDK_CRASH_EVENT_MISSING_MANDATORY_FIELDS }
            )
            return
        }

        // The crashed run started at this time: a view written before it belongs to an earlier run.
        val runStartedAt = timeSinceAppStartMs?.let { timestamp - it }

        rumFeature.withWriteContext { datadogContext, writeScope ->
            val target = resolveTarget(lastSlot, timestamp, runStartedAt, datadogContext.time.serverTimeOffsetMs)
            if (target == CrashTarget.NotReported) return@withWriteContext
            val lastViewEvent = (target as? CrashTarget.LastView)?.view
                // No foreground state is persisted with a native crash: one with no view to attach
                // to is taken as a crash during the application launch.
                ?: newSessionView(datadogContext, timestamp, inForeground = true)
                ?: return@withWriteContext
            val toSendErrorEvent = resolveErrorEventFromViewEvent(
                datadogContext,
                ErrorEvent.SourceType.tryFromSource(sourceType),
                ErrorEvent.Category.EXCEPTION,
                errorLogMessage,
                timestamp,
                timeSinceAppStartMs,
                stacktrace,
                signalName,
                null,
                lastViewEvent
            ).let { errorEvent ->
                if (crashId == null) {
                    errorEvent
                } else {
                    errorEvent.copy(
                        date = crashDate ?: errorEvent.date,
                        error = errorEvent.error.copy(id = crashId, beacon = beacon)
                    )
                }
            }
            writeScope {
                writeCrash(it, rumWriter, toSendErrorEvent, lastViewEvent, target == CrashTarget.NewSession)
            }
        }
    }

    @WorkerThread
    @RequiresApi(Build.VERSION_CODES.R)
    override fun handleAnrCrash(
        anrExitInfo: ApplicationExitInfo,
        lastRumViewEventJson: JsonObject?,
        previousExitTimestamp: Long?,
        isLastExit: Boolean,
        rumWriter: DataWriter<Any>
    ) {
        val lastSlot = readSlot(lastRumViewEventJson)
        // A view started after the ANR belongs to a later run: this ANR was handled then, or it
        // belongs to no view known here and is not the last thing that happened to the process.
        if (lastSlot is LastSlot.View && anrExitInfo.timestamp <= lastSlot.view.date) return
        val rumFeature = sdkCore.getFeature(Feature.RUM_FEATURE_NAME)

        if (rumFeature == null) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { INFO_RUM_FEATURE_NOT_REGISTERED }
            )
            return
        }

        rumFeature.withWriteContext(
            withFeatureContexts = setOf(Feature.RUM_FEATURE_NAME)
        ) { datadogContext, writeScope ->
            // FLASHCAT FORK - the crashed run started after the exit before it: a view or marker
            // written before then belongs to an earlier run.
            val target = resolveTarget(
                lastSlot,
                anrExitInfo.timestamp,
                previousExitTimestamp,
                datadogContext.time.serverTimeOffsetMs
            )
            if (target == CrashTarget.NotReported) return@withWriteContext
            // FLASHCAT FORK - with no view to tell when it happened, only the ANR that ended the last
            // run of the process is reported: an older one, from before the SDK kept any record of
            // the app, would be reported in a session of its own with no view newer than it.
            if (target == CrashTarget.NewSession && !isLastExit) return@withWriteContext

            // means we are too late, last view event belongs to the ongoing session
            if (target is CrashTarget.LastView && target.view.session.id == datadogContext.rumSessionId) {
                return@withWriteContext
            }

            val lastFatalAnrSent = sdkCore.lastFatalAnrSent
            if (anrExitInfo.timestamp == lastFatalAnrSent) return@withWriteContext

            val lastViewEvent = (target as? CrashTarget.LastView)?.view
                ?: newSessionView(
                    datadogContext,
                    anrExitInfo.timestamp,
                    // The rule crash beacons use: a process the user could see.
                    inForeground = CrashBeacon.isUserVisible(anrExitInfo.importance)
                )
                ?: return@withWriteContext

            val threadDumps = readThreadsDump(anrExitInfo)
            if (threadDumps.isEmpty()) return@withWriteContext

            val toSendErrorEvent = resolveErrorEventFromViewEvent(
                datadogContext,
                ErrorEvent.SourceType.ANDROID,
                ErrorEvent.Category.ANR,
                ANRDetectorRunnable.ANR_MESSAGE,
                anrExitInfo.timestamp,
                // TODO RUM-3780 support reporting `error.time_since_app_start` for fatal ANRs
                null,
                threadDumps.mainThread?.stack.orEmpty(),
                ANRException::class.java.canonicalName.orEmpty(),
                threadDumps,
                lastViewEvent
            )
            writeScope {
                // mark it before creating RUM events, so that we don't handle it again if code crashed after
                // RUM events are written
                sdkCore.writeLastFatalAnrSent(anrExitInfo.timestamp)
                writeCrash(it, rumWriter, toSendErrorEvent, lastViewEvent, target == CrashTarget.NewSession)
            }
        }
    }

    // endregion

    // region Internal

    /**
     * FLASHCAT FORK - a session kept only on error uploaded nothing before it crashed: its last view
     * is the only one the intake will ever get, and the session is built out of the first view it
     * sees. So that view goes first, and only along with a crash that will actually be stored - a
     * view on its own would be a kept session without the error it was kept for.
     *
     * A view made up for a crash of its own session ([isNewSession]) is the whole session: it is
     * always written, however long ago the crash was.
     */
    @WorkerThread
    private fun writeCrash(
        writer: EventBatchWriter,
        rumWriter: DataWriter<Any>,
        errorEvent: ErrorEvent,
        lastViewEvent: ViewEvent,
        isNewSession: Boolean
    ) {
        val rumDataWriter = rumWriter as? RumDataWriter
        if (lastViewEvent.isOnlyKnownHere && rumDataWriter != null) {
            val serializedError = rumDataWriter.serialize(errorEvent) ?: return
            if (!rumDataWriter.write(writer, updateViewEvent(lastViewEvent), EventType.CRASH)) return
            rumDataWriter.writeSerialized(writer, serializedError, EventType.CRASH)
            return
        }
        rumWriter.write(writer, errorEvent, EventType.CRASH)
        if (isNewSession || lastViewEvent.isWithinSessionAvailability || lastViewEvent.isOnlyKnownHere) {
            rumWriter.write(writer, updateViewEvent(lastViewEvent), EventType.CRASH)
        }
    }

    /**
     * FLASHCAT FORK - what the slot the last view is written to holds: the last view of the last
     * session that wrote one, or the marker of a session drawn as not tracked (see
     * [untrackedSessionMarker]), whichever came last.
     */
    private fun readSlot(json: JsonObject?): LastSlot? {
        if (json == null) return null
        if (json.has(UNTRACKED_SESSION_KEY)) {
            val date = (json.get(UNTRACKED_SESSION_KEY) as? JsonObject)?.get(MARKER_DATE_KEY) as? JsonPrimitive
            return date?.takeIf { it.isNumber }?.let { LastSlot.Untracked(it.asLong) }
        }
        return (rumEventDeserializer.deserialize(json) as? ViewEvent)?.let { LastSlot.View(it) }
    }

    /**
     * FLASHCAT FORK - where a crash reported on the next launch goes.
     *
     * - The last view, when it can belong to the crashed run: last updated since the run started
     * ([runStartedAt], when known) and no more than [VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD] before
     * the crash. Older, it belongs to an earlier run that left it unconsumed.
     * - Nowhere, when the crashed run's session was drawn as not tracked: it was left out of the
     * sample on purpose, and reporting its crash in a session of its own would count the crashes
     * of untracked sessions on top of the tracked ones the sample stands for.
     * - A session of its own otherwise: the crash belongs to no session known here (none was
     * drawn yet, the one drawn wrote no view, or the view was thrown away with a session kept on
     * error that ended without one).
     */
    private fun resolveTarget(
        lastSlot: LastSlot?,
        crashTimestamp: Long,
        runStartedAt: Long?,
        serverTimeOffsetMs: Long
    ): CrashTarget {
        return when (lastSlot) {
            is LastSlot.View -> {
                val view = lastSlot.view
                // On the device clock, like the crash: a view's date is corrected by the server
                // offset, taken as the current one (the view's own is not kept).
                val viewUpdatedAt = view.date - serverTimeOffsetMs +
                    TimeUnit.NANOSECONDS.toMillis(view.view.timeSpent)
                val isOfCrashedRun = runStartedAt == null || viewUpdatedAt >= runStartedAt
                val isRecent = crashTimestamp - viewUpdatedAt <= VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD
                if (isOfCrashedRun && isRecent) CrashTarget.LastView(view) else CrashTarget.NewSession
            }
            is LastSlot.Untracked -> {
                val isOfCrashedRun = lastSlot.date <= crashTimestamp &&
                    (runStartedAt == null || lastSlot.date >= runStartedAt)
                if (isOfCrashedRun) CrashTarget.NotReported else CrashTarget.NewSession
            }
            null -> CrashTarget.NewSession
        }
    }

    /**
     * FLASHCAT FORK - the one view of the session a crash with no known session is reported in,
     * drawn like a new session would be now: [RumViewManagerScope]'s "ApplicationLaunch" view in
     * the foreground, its "Background" view in the background, starting at the crash. Null when the
     * draw does not keep the session. Late crashes are never retried (the NDK report is deleted
     * once read, a fatal ANR is marked sent before it is written), so the ids and the draw are
     * plain random ones.
     */
    private fun newSessionView(
        datadogContext: DatadogContext,
        crashTimestamp: Long,
        inForeground: Boolean
    ): ViewEvent? {
        val session = drawNewSession() ?: return null
        val deviceInfo = datadogContext.deviceInfo
        val user = datadogContext.userInfo
        return ViewEvent(
            date = crashTimestamp + datadogContext.time.serverTimeOffsetMs,
            application = ViewEvent.Application(
                id = session.applicationId,
                currentLocale = deviceInfo.localeInfo.currentLocale
            ),
            service = datadogContext.service,
            version = datadogContext.version,
            buildVersion = datadogContext.versionCode.toString(),
            buildId = datadogContext.appBuildId,
            ddtags = buildDDTagsString(datadogContext),
            session = ViewEvent.ViewEventSession(
                id = UUID.randomUUID().toString(),
                type = ViewEvent.ViewEventSessionType.USER,
                // Absent, rather than false, for a sampled session - as on every other view.
                sampledForError = session.sampledForError.takeIf { it }
            ),
            source = ViewEvent.ViewEventSource.tryFromSource(datadogContext.source, sdkCore.internalLogger),
            view = ViewEvent.ViewEventView(
                id = UUID.randomUUID().toString(),
                url = if (inForeground) {
                    RumViewManagerScope.RUM_APP_LAUNCH_VIEW_URL
                } else {
                    RumViewManagerScope.RUM_BACKGROUND_VIEW_URL
                },
                name = if (inForeground) {
                    RumViewManagerScope.RUM_APP_LAUNCH_VIEW_NAME
                } else {
                    RumViewManagerScope.RUM_BACKGROUND_VIEW_NAME
                },
                timeSpent = 1L,
                action = ViewEvent.Action(0),
                error = ViewEvent.Error(0),
                crash = ViewEvent.Crash(0),
                resource = ViewEvent.Resource(0)
            ),
            usr = if (user.hasUserData()) {
                ViewEvent.Usr(
                    id = user.id,
                    name = user.name,
                    email = user.email,
                    anonymousId = user.anonymousId,
                    additionalProperties = user.additionalProperties.toMutableMap()
                )
            } else {
                null
            },
            account = datadogContext.accountInfo?.let {
                ViewEvent.Account(
                    id = it.id,
                    name = it.name,
                    additionalProperties = it.extraInfo.toMutableMap()
                )
            },
            os = ViewEvent.Os(
                name = deviceInfo.osName,
                version = deviceInfo.osVersion,
                versionMajor = deviceInfo.osMajorVersion
            ),
            device = ViewEvent.Device(
                type = deviceInfo.deviceType.toViewSchemaType(),
                name = deviceInfo.deviceName,
                model = deviceInfo.deviceModel,
                brand = deviceInfo.deviceBrand,
                architecture = deviceInfo.architecture
            ),
            dd = ViewEvent.Dd(
                documentVersion = 1L,
                // A session kept only because of its error stands for itself: 0, as its own events
                // would have reported.
                configuration = ViewEvent.Configuration(sessionSampleRate = session.sampleRate)
            )
        )
    }

    @Suppress("LongMethod", "LongParameterList")
    private fun resolveErrorEventFromViewEvent(
        datadogContext: DatadogContext,
        sourceType: ErrorEvent.SourceType,
        category: ErrorEvent.Category,
        errorLogMessage: String,
        timestamp: Long,
        timeSinceAppStartMs: Long?,
        stacktrace: String,
        errorType: String,
        threadDumps: List<ThreadDump>?,
        viewEvent: ViewEvent
    ): ErrorEvent {
        val connectivity = viewEvent.connectivity?.let {
            val connectivityStatus =
                ErrorEvent.Status.valueOf(it.status.name)
            val connectivityInterfaces =
                it.interfaces?.map { ErrorEvent.Interface.valueOf(it.name) }
            val cellular = ErrorEvent.Cellular(
                it.cellular?.technology,
                it.cellular?.carrierName
            )
            ErrorEvent.Connectivity(connectivityStatus, connectivityInterfaces, cellular = cellular)
        }
        val additionalProperties = viewEvent.context?.additionalProperties ?: mutableMapOf()
        val additionalUserProperties = viewEvent.usr?.additionalProperties ?: mutableMapOf()
        val user = viewEvent.usr
        val hasUserInfo = user?.id != null || user?.anonymousId != null || user?.name != null ||
            user?.email != null || additionalUserProperties.isNotEmpty()
        val account = viewEvent.account
        val deviceInfo = datadogContext.deviceInfo

        return ErrorEvent(
            // TODO RUM-3832 NDK/fatal ANRs reported should have build ID from the previous application run
            date = timestamp + datadogContext.time.serverTimeOffsetMs,
            buildId = datadogContext.appBuildId,
            application = ErrorEvent.Application(viewEvent.application.id),
            service = viewEvent.service,
            session = ErrorEvent.ErrorEventSession(
                viewEvent.session.id,
                ErrorEvent.ErrorEventSessionType.USER
            ),
            source = viewEvent.source?.toJson()?.asString?.let {
                ErrorEvent.ErrorEventSource.tryFromSource(
                    it,
                    sdkCore.internalLogger
                )
            },
            view = ErrorEvent.ErrorEventView(
                id = viewEvent.view.id,
                name = viewEvent.view.name,
                referrer = viewEvent.view.referrer,
                url = viewEvent.view.url
            ),
            usr = if (!hasUserInfo) {
                null
            } else {
                ErrorEvent.Usr(
                    user?.id,
                    user?.name,
                    user?.email,
                    user?.anonymousId,
                    additionalUserProperties
                )
            },
            account = account?.let {
                ErrorEvent.Account(
                    id = it.id,
                    name = it.name,
                    additionalProperties = it.additionalProperties
                )
            },
            connectivity = connectivity,
            os = ErrorEvent.Os(
                name = deviceInfo.osName,
                version = deviceInfo.osVersion,
                versionMajor = deviceInfo.osMajorVersion
            ),
            device = ErrorEvent.Device(
                type = deviceInfo.deviceType.toErrorSchemaType(),
                name = deviceInfo.deviceName,
                model = deviceInfo.deviceModel,
                brand = deviceInfo.deviceBrand,
                architecture = deviceInfo.architecture
            ),
            dd = ErrorEvent.Dd(
                session = ErrorEvent.DdSession(),
                configuration = ErrorEvent.Configuration(sessionSampleRate = viewEvent.sampleRate)
            ),
            context = ErrorEvent.Context(additionalProperties = additionalProperties),
            error = ErrorEvent.Error(
                id = UUID.randomUUID().toString(),
                message = errorLogMessage,
                source = ErrorEvent.ErrorSource.SOURCE,
                stack = stacktrace,
                isCrash = true,
                type = errorType,
                sourceType = sourceType,
                category = category,
                threads = threadDumps?.map {
                    ErrorEvent.Thread(
                        it.name,
                        it.crashed,
                        it.stack,
                        it.state
                    )
                },
                timeSinceAppStart = timeSinceAppStartMs
            ),
            version = viewEvent.version,
            ddtags = buildDDTagsString(datadogContext)
        )
    }

    @Suppress("ReturnCount")
    @RequiresApi(Build.VERSION_CODES.R)
    private fun readThreadsDump(anrExitInfo: ApplicationExitInfo): List<ThreadDump> {
        val traceInputStream = try {
            anrExitInfo.traceInputStream
        } catch (ioe: IOException) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { OPEN_ANR_TRACE_ERROR },
                ioe
            )
            return emptyList()
        }
        if (traceInputStream == null) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { MISSING_ANR_TRACE }
            )
            return emptyList()
        }

        return androidTraceParser.parse(traceInputStream)
    }

    private fun updateViewEvent(lastViewEvent: ViewEvent): ViewEvent {
        val currentCrash = lastViewEvent.view.crash
        val newCrash = currentCrash?.copy(count = currentCrash.count + 1) ?: ViewEvent.Crash(1)
        return lastViewEvent.copy(
            view = lastViewEvent.view.copy(
                crash = newCrash,
                isActive = false
            ),
            dd = lastViewEvent.dd.copy(
                documentVersion = lastViewEvent.dd.documentVersion + 1
            )
        )
    }

    private val ViewEvent.sampleRate: Float
        get() = dd.configuration?.sessionSampleRate?.toFloat() ?: 0f

    /**
     * FLASHCAT FORK - a session kept only on error uploaded nothing before it crashed: the view
     * written locally is the only one the intake will ever get, however old it is by now.
     */
    private val ViewEvent.isOnlyKnownHere: Boolean
        get() = session.sampledForError == true

    private val ViewEvent.isWithinSessionAvailability: Boolean
        get() {
            val now = sdkCore.timeProvider.getDeviceTimestampMillis()
            val sessionsTimeDifference = now - this.date
            return sessionsTimeDifference < VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD
        }

    private val List<ThreadDump>.mainThread: ThreadDump?
        get() = firstOrNull { it.name == "main" }

    private fun ErrorEvent.SourceType.Companion.tryFromSource(
        sourceType: String?
    ): ErrorEvent.SourceType {
        return if (sourceType != null) {
            try {
                ErrorEvent.SourceType.fromJson(sourceType)
            } catch (e: NoSuchElementException) {
                sdkCore.internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.TELEMETRY,
                    { "Error parsing source type from NDK crash event: $sourceType" },
                    e
                )
                ErrorEvent.SourceType.NDK
            }
        } else {
            ErrorEvent.SourceType.NDK
        }
    }

    private val DatadogContext.rumSessionId: String?
        get() = featuresContext[Feature.RUM_FEATURE_NAME]
            .orEmpty()[RumContext.SESSION_ID] as? String

    // endregion

    companion object {
        internal const val INFO_RUM_FEATURE_NOT_REGISTERED =
            "RUM feature is not registered, won't report NDK crash info as RUM error."
        internal const val NDK_CRASH_EVENT_MISSING_MANDATORY_FIELDS =
            "RUM feature received a NDK crash event" +
                " where one or more mandatory (timestamp, signalName, stacktrace," +
                " message, lastViewEvent) fields are either missing or have wrong type."
        internal const val MISSING_ANR_TRACE = "Last known exit reason has no trace information" +
            " attached, cannot report fatal ANR."
        internal const val OPEN_ANR_TRACE_ERROR = "Cannot open trace for the last known exit reason."

        internal val VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD = TimeUnit.HOURS.toMillis(4)

        private const val UNTRACKED_SESSION_KEY = "fc_untracked_session"
        private const val MARKER_SESSION_ID_KEY = "session_id"
        private const val MARKER_DATE_KEY = "date"

        /**
         * FLASHCAT FORK - written to the slot of the last view when a session is drawn as not
         * tracked, so a crash of that session is known for one at the next launch. Device time.
         */
        internal fun untrackedSessionMarker(sessionId: String, timestamp: Long): ByteArray {
            val marker = JsonObject().apply {
                addProperty(MARKER_SESSION_ID_KEY, sessionId)
                addProperty(MARKER_DATE_KEY, timestamp)
            }
            return JsonObject().apply { add(UNTRACKED_SESSION_KEY, marker) }.toString().toByteArray(Charsets.UTF_8)
        }
    }

    /** FLASHCAT FORK - the session a crash with no known session is reported in. */
    internal data class NewSession(
        val applicationId: String,
        val sampleRate: Float,
        val sampledForError: Boolean
    )

    private sealed class LastSlot {
        class View(val view: ViewEvent) : LastSlot()
        class Untracked(val date: Long) : LastSlot()
    }

    private sealed class CrashTarget {
        class LastView(val view: ViewEvent) : CrashTarget()
        object NewSession : CrashTarget()
        object NotReported : CrashTarget()
    }
}
