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
import com.datadog.android.api.feature.EventWriteScope
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
    private val drawNewSession: () -> CrashSession? = {
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
                ?: sessionViewFor(
                    target,
                    datadogContext,
                    // The beacon's date, when there is one: the error is dated by it too.
                    crashDate ?: (timestamp + datadogContext.time.serverTimeOffsetMs),
                    inForeground = true
                )
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
                writeCrash(it, rumWriter, toSendErrorEvent, lastViewEvent, target !is CrashTarget.LastView)
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
            val serverTimeOffsetMs = datadogContext.time.serverTimeOffsetMs
            // A view started after the ANR belongs to a later run: this ANR was handled then, or it
            // belongs to no view known here and is not the last thing that happened to the process.
            // FLASHCAT FORK - on the device clock, like the ANR: the view's date is server time.
            if (lastSlot is LastSlot.View && anrExitInfo.timestamp <= lastSlot.view.date - serverTimeOffsetMs) {
                return@withWriteContext
            }
            // FLASHCAT FORK - first: an ANR already decided is not decided, nor marked, again.
            if (anrExitInfo.timestamp == sdkCore.lastFatalAnrSent) return@withWriteContext
            // FLASHCAT FORK - the crashed run started after the exit before it: a view or marker
            // written before then belongs to an earlier run.
            val target = resolveTarget(
                lastSlot,
                anrExitInfo.timestamp,
                previousExitTimestamp,
                serverTimeOffsetMs
            )
            // FLASHCAT FORK - the ANR stays the last one of the process until another ends a run, so
            // a decision not to report it is marked like a report: left unmarked, a later launch
            // would decide again, from a slot this launch may have replaced, and could report it.
            if (target == CrashTarget.NotReported) {
                markFatalAnrHandled(anrExitInfo, writeScope)
                return@withWriteContext
            }
            // FLASHCAT FORK - with no view to tell when it happened, only the ANR that ended the last
            // run of the process is reported: an older one, from before the SDK kept any record of
            // the app, would be reported in a session of its own with no view newer than it.
            if (target == CrashTarget.NewSession && !isLastExit) {
                markFatalAnrHandled(anrExitInfo, writeScope)
                return@withWriteContext
            }

            // means we are too late, last view event belongs to the ongoing session
            if (target is CrashTarget.LastView && target.view.session.id == datadogContext.rumSessionId) {
                return@withWriteContext
            }

            // FLASHCAT FORK - before a session is drawn for it: an unreadable dump leaves the ANR to
            // the next launch, which must not find a draw already made.
            val threadDumps = readThreadsDump(anrExitInfo)
            if (threadDumps.isEmpty()) return@withWriteContext

            val lastViewEvent = (target as? CrashTarget.LastView)?.view
                ?: sessionViewFor(
                    target,
                    datadogContext,
                    anrExitInfo.timestamp + serverTimeOffsetMs,
                    // The rule crash beacons use: a process the user could see.
                    inForeground = CrashBeacon.isUserVisible(anrExitInfo.importance)
                )
                ?: run {
                    // FLASHCAT FORK - the draw left its session out: drawn again later, an ANR
                    // would be kept more often than the rate says.
                    markFatalAnrHandled(anrExitInfo, writeScope)
                    return@withWriteContext
                }

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
                writeCrash(it, rumWriter, toSendErrorEvent, lastViewEvent, target !is CrashTarget.LastView)
            }
        }
    }

    // endregion

    // region Internal

    /**
     * FLASHCAT FORK - a fatal ANR decided for good is not decided again. A thread dump that could
     * not be read is not a decision: it is left for the next launch to read.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun markFatalAnrHandled(anrExitInfo: ApplicationExitInfo, writeScope: EventWriteScope) {
        writeScope { sdkCore.writeLastFatalAnrSent(anrExitInfo.timestamp) }
    }

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
     * session that wrote one, or the marker of the last session drawn (see [untrackedSessionMarker]
     * and [trackedSessionMarker]), whichever came last.
     */
    private fun readSlot(json: JsonObject?): LastSlot? {
        if (json == null) return null
        (json.get(UNTRACKED_SESSION_KEY) as? JsonObject)?.let { marker ->
            return marker.longOrNull(MARKER_DATE_KEY)?.let { LastSlot.Untracked(it) }
        }
        (json.get(TRACKED_SESSION_KEY) as? JsonObject)?.let { marker ->
            return readTrackedMarker(marker)
        }
        return (rumEventDeserializer.deserialize(json) as? ViewEvent)?.let { LastSlot.View(it) }
    }

    @Suppress("ReturnCount")
    private fun readTrackedMarker(marker: JsonObject): LastSlot.Tracked? {
        val date = marker.longOrNull(MARKER_DATE_KEY) ?: return null
        val sessionId = marker.stringOrNull(MARKER_SESSION_ID_KEY) ?: return null
        val applicationId = marker.stringOrNull(MARKER_APPLICATION_ID_KEY) ?: return null
        val sampleRate = (marker.get(MARKER_SAMPLE_RATE_KEY) as? JsonPrimitive)
            ?.takeIf { it.isNumber }?.asFloat ?: return null
        val sampledForError = (marker.get(MARKER_SAMPLED_FOR_ERROR_KEY) as? JsonPrimitive)
            ?.takeIf { it.isBoolean }?.asBoolean ?: return null
        return LastSlot.Tracked(date, CrashSession(sessionId, applicationId, sampleRate, sampledForError))
    }

    private fun JsonObject.longOrNull(key: String): Long? =
        (get(key) as? JsonPrimitive)?.takeIf { it.isNumber }?.asLong

    private fun JsonObject.stringOrNull(key: String): String? =
        (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.asString

    /**
     * FLASHCAT FORK - where a crash reported on the next launch goes.
     *
     * - The last view, when it can belong to the crashed run: last updated since the run started
     * ([runStartedAt], when known) and no more than [VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD] before
     * the crash. Older, it belongs to an earlier run that left it unconsumed.
     * - Nowhere, when the crashed run's session was drawn as not tracked: it was left out of the
     * sample on purpose, and reporting its crash in a session of its own would count the crashes
     * of untracked sessions on top of the tracked ones the sample stands for.
     * - The crashed run's session, as it was drawn, when it was kept but wrote no view yet, no more
     * than [VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD] before the crash: drawing it again would keep
     * its crash only at the rate squared.
     * - A session of its own otherwise: the crash belongs to no session known here (none was
     * drawn yet, or the view was thrown away with a session kept on error that ended without one).
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
                if (lastSlot.date.isOfCrashedRun(crashTimestamp, runStartedAt)) {
                    CrashTarget.NotReported
                } else {
                    CrashTarget.NewSession
                }
            }
            is LastSlot.Tracked -> {
                // As recent as a last view must be: older, the session it marks has ended.
                val isRecent = crashTimestamp - lastSlot.date <= VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD
                if (lastSlot.date.isOfCrashedRun(crashTimestamp, runStartedAt) && isRecent) {
                    CrashTarget.ViewlessSession(lastSlot.session)
                } else {
                    CrashTarget.NewSession
                }
            }
            null -> CrashTarget.NewSession
        }
    }

    /** FLASHCAT FORK - a marker dated within the crashed run, on the device clock like the crash. */
    private fun Long.isOfCrashedRun(crashTimestamp: Long, runStartedAt: Long?): Boolean =
        this <= crashTimestamp && (runStartedAt == null || this >= runStartedAt)

    /**
     * FLASHCAT FORK - the view a crash with no view to attach to is reported in: one of the crashed
     * run's own session when its marker says it was kept, else one of a session drawn now.
     */
    private fun sessionViewFor(
        target: CrashTarget,
        datadogContext: DatadogContext,
        date: Long,
        inForeground: Boolean
    ): ViewEvent? {
        val session = (target as? CrashTarget.ViewlessSession)?.session ?: drawNewSession() ?: return null
        return newSessionView(session, datadogContext, date, inForeground)
    }

    /**
     * FLASHCAT FORK - the one view a crash with no view to attach to is reported in, in [session]:
     * [RumViewManagerScope]'s "ApplicationLaunch" view in the foreground, its "Background" view in
     * the background, starting at the crash. Late crashes are never retried (the NDK report is
     * deleted once read, a fatal ANR is marked sent before it is written), so the view id is a
     * plain random one. [date] is the crash's, on the server clock, as the error reports it.
     */
    private fun newSessionView(
        session: CrashSession,
        datadogContext: DatadogContext,
        date: Long,
        inForeground: Boolean
    ): ViewEvent {
        val deviceInfo = datadogContext.deviceInfo
        val user = datadogContext.userInfo
        return ViewEvent(
            date = date,
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
                id = session.id,
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
        private const val TRACKED_SESSION_KEY = "fc_tracked_session"
        private const val MARKER_SESSION_ID_KEY = "session_id"
        private const val MARKER_APPLICATION_ID_KEY = "application_id"
        private const val MARKER_SAMPLE_RATE_KEY = "sample_rate"
        private const val MARKER_SAMPLED_FOR_ERROR_KEY = "sampled_for_error"
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

        /**
         * FLASHCAT FORK - written to the slot of the last view when a session is drawn as kept, so
         * a crash of that session before its first view is reported in it at the next launch, as
         * it was drawn. Device time.
         */
        internal fun trackedSessionMarker(session: CrashSession, timestamp: Long): ByteArray {
            val marker = JsonObject().apply {
                addProperty(MARKER_SESSION_ID_KEY, session.id)
                addProperty(MARKER_APPLICATION_ID_KEY, session.applicationId)
                addProperty(MARKER_SAMPLE_RATE_KEY, session.sampleRate)
                addProperty(MARKER_SAMPLED_FOR_ERROR_KEY, session.sampledForError)
                addProperty(MARKER_DATE_KEY, timestamp)
            }
            return JsonObject().apply { add(TRACKED_SESSION_KEY, marker) }.toString().toByteArray(Charsets.UTF_8)
        }
    }

    /**
     * FLASHCAT FORK - the session a crash with no view to attach to is reported in: the crashed
     * run's own, from its marker, or one drawn for it now.
     */
    internal data class CrashSession(
        val id: String,
        val applicationId: String,
        val sampleRate: Float,
        val sampledForError: Boolean
    )

    private sealed class LastSlot {
        class View(val view: ViewEvent) : LastSlot()
        class Untracked(val date: Long) : LastSlot()
        class Tracked(val date: Long, val session: CrashSession) : LastSlot()
    }

    private sealed class CrashTarget {
        class LastView(val view: ViewEvent) : CrashTarget()
        class ViewlessSession(val session: CrashSession) : CrashTarget()
        object NewSession : CrashTarget()
        object NotReported : CrashTarget()
    }
}
