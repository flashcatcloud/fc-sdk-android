/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.NoOpDataWriter
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.internal.net.FirstPartyHostHeaderTypeResolver
import com.datadog.android.internal.profiling.ProfilerStopEvent
import com.datadog.android.rum.BeforeSamplingCallback
import com.datadog.android.rum.BeforeSamplingContext
import com.datadog.android.rum.RumSessionListener
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.internal.domain.InfoProvider
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.domain.accessibility.AccessibilitySnapshotManager
import com.datadog.android.rum.internal.domain.battery.BatteryInfo
import com.datadog.android.rum.internal.domain.display.DisplayInfo
import com.datadog.android.rum.internal.instrumentation.insights.InsightsCollector
import com.datadog.android.rum.internal.metric.SessionMetricDispatcher
import com.datadog.android.rum.internal.metric.slowframes.SlowFramesListener
import com.datadog.android.rum.internal.remoteconfig.DrawnConfiguration
import com.datadog.android.rum.internal.remoteconfig.RemoteConfigStore
import com.datadog.android.rum.internal.remoteconfig.decodeCustomValues
import com.datadog.android.rum.internal.startup.RumSessionScopeStartupManager
import com.datadog.android.rum.internal.utils.percent
import com.datadog.android.rum.internal.vitals.VitalMonitor
import com.datadog.android.rum.metric.interactiontonextview.LastInteractionIdentifier
import com.datadog.android.rum.metric.networksettled.InitialResourceIdentifier
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

@Suppress("LongParameterList")
internal class RumSessionScope(
    override val parentScope: RumScope,
    private val sdkCore: InternalSdkCore,
    private val sessionEndedMetricDispatcher: SessionMetricDispatcher,
    internal val sampleRate: Float,
    internal val backgroundTrackingEnabled: Boolean,
    trackFrustrations: Boolean,
    viewChangedListener: RumViewChangedListener?,
    internal val firstPartyHostHeaderTypeResolver: FirstPartyHostHeaderTypeResolver,
    cpuVitalMonitor: VitalMonitor,
    memoryVitalMonitor: VitalMonitor,
    frameRateVitalMonitor: VitalMonitor,
    private val sessionListener: RumSessionListener?,
    applicationDisplayed: Boolean,
    networkSettledResourceIdentifier: InitialResourceIdentifier,
    lastInteractionIdentifier: LastInteractionIdentifier?,
    slowFramesListener: SlowFramesListener?,
    accessibilitySnapshotManager: AccessibilitySnapshotManager,
    batteryInfoProvider: InfoProvider<BatteryInfo>,
    displayInfoProvider: InfoProvider<DisplayInfo>,
    private val sessionInactivityNanos: Long = DEFAULT_SESSION_INACTIVITY_NS,
    private val sessionMaxDurationNanos: Long = DEFAULT_SESSION_MAX_DURATION_NS,
    rumSessionTypeOverride: RumSessionType?,
    private val rumSessionScopeStartupManagerFactory: () -> RumSessionScopeStartupManager,
    insightsCollector: InsightsCollector,
    // FLASHCAT FORK - rates the console can change without the app shipping a new release. Null
    // when the app did not opt in, which is what keeps this whole path inert by default.
    private val remoteConfig: RemoteConfigStore? = null,
    // FLASHCAT FORK - fired after each draw, so the stored configuration is re-fetched on the only
    // rhythm that can matter: a changed value can only apply to the next session anyway.
    private val onSessionDrawn: () -> Unit = {},
    // FLASHCAT FORK - the host application's last word on the draw, consulted after the console's
    // rate. Null unless the app set one.
    private val beforeSampling: BeforeSamplingCallback? = null,
    // FLASHCAT FORK - whether the host application has asked for this user's sessions to be
    // collected regardless of the rates. Passed in rather than owned here because it outlives any
    // one session: the application asked for the user, not for whichever session happened to be
    // running when it asked. [RumApplicationScope] holds it and hands it to every session it makes.
    internal var forcedSession: Boolean = false
) : RumScope {

    // FLASHCAT FORK - the rate the current session's events report as their configured sample
    // rate. It is the rate the draw actually used - the console's where it set one, the app's hook
    // where that had the last word - rather than whatever was passed to init, so that anything
    // extrapolating from it lands on the population the session was drawn from. A forced session is
    // the one case where the two part company: see `renewSession`.
    internal var effectiveSampleRate: Float = sampleRate

    // FLASHCAT FORK - the configuration the current session was drawn under, so its events can
    // report the rates and the settings version that actually decided them. Null when the app did
    // not opt in: events then keep reporting the init values, which in that case are the values
    // the draw used anyway.
    internal var drawnConfiguration: DrawnConfiguration? = null
        private set

    internal var sessionId = RumContext.NULL_UUID
    internal var sessionState: State = State.NOT_TRACKED

    private var startReason: StartReason = StartReason.USER_APP_LAUNCH
    internal var isActive: Boolean = true
    private val sessionStartNs = AtomicLong(sdkCore.timeProvider.getDeviceElapsedTimeNanos())

    private val lastUserInteractionNs = AtomicLong(0L)

    private val random = SecureRandom()

    private val noOpWriter = NoOpDataWriter<Any>()

    private var rumSessionScopeStartupManager: RumSessionScopeStartupManager? = null

    @Suppress("LongParameterList")
    internal var childScope: RumViewManagerScope? = RumViewManagerScope(
        parentScope = this,
        sdkCore = sdkCore,
        sessionEndedMetricDispatcher = sessionEndedMetricDispatcher,
        backgroundTrackingEnabled = backgroundTrackingEnabled,
        trackFrustrations = trackFrustrations,
        viewChangedListener = viewChangedListener,
        firstPartyHostHeaderTypeResolver = firstPartyHostHeaderTypeResolver,
        cpuVitalMonitor = cpuVitalMonitor,
        memoryVitalMonitor = memoryVitalMonitor,
        frameRateVitalMonitor = frameRateVitalMonitor,
        applicationDisplayed = applicationDisplayed,
        sampleRate = sampleRate,
        initialResourceIdentifier = networkSettledResourceIdentifier,
        slowFramesListener = slowFramesListener,
        lastInteractionIdentifier = lastInteractionIdentifier,
        rumSessionTypeOverride = rumSessionTypeOverride,
        accessibilitySnapshotManager = accessibilitySnapshotManager,
        batteryInfoProvider = batteryInfoProvider,
        displayInfoProvider = displayInfoProvider,
        insightsCollector = insightsCollector
    )

    internal val activeView: RumViewScope?
        get() = if (isActive() && childScope != null) {
            childScope?.activeView
        } else {
            null
        }

    enum class State(val asString: String) {
        NOT_TRACKED("NOT_TRACKED"),
        TRACKED("TRACKED"),
        EXPIRED("EXPIRED");

        companion object {
            fun fromString(string: String?): State? {
                return values().firstOrNull { it.asString == string }
            }
        }
    }

    enum class StartReason(val asString: String) {
        USER_APP_LAUNCH("user_app_launch"),
        INACTIVITY_TIMEOUT("inactivity_timeout"),
        MAX_DURATION("max_duration"),
        BACKGROUND_LAUNCH("background_launch"),
        PREWARM("prewarm"),
        FROM_NON_INTERACTIVE_SESSION("from_non_interactive_session"),
        EXPLICIT_STOP("explicit_stop")
        ;

        companion object {
            fun fromString(string: String?): StartReason? {
                return values().firstOrNull { it.asString == string }
            }
        }
    }

    // region RumScope

    @WorkerThread
    override fun handleEvent(
        event: RumRawEvent,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>
    ): RumScope? {
        if (event is RumRawEvent.ResetSession) {
            // FLASHCAT FORK - two kinds of session must not be renewed here. A stopped one is
            // draining: renewing it would mint a session id under a scope whose own context already
            // reports the session as inactive, and would announce that session to the host
            // application's listener. A forced one would only ever be replaced by an identical
            // forced session, so the renewal buys nothing and costs the view the user is on.
            if (isActive && !forcedSession) {
                renewSession(event.eventTime, StartReason.EXPLICIT_STOP)
            }
        } else if (event is RumRawEvent.SetForcedSession && isActive) {
            // FLASHCAT FORK - the escape hatch for "collect this user NOW": the application knows
            // who needs debugging, the SDK only provides the switch. From here on every draw keeps
            // the session. [RumApplicationScope] remembers the same thing for the sessions that
            // come after this one, including the ones that follow a `stopSession()`.
            forcedSession = true
            // A session already being collected keeps running: RUM cannot retro-collect what a
            // running session already dropped, so cutting it in two would gain nothing. One that
            // was NOT collected restarts now, so a collected one takes its place.
            if (sessionState != State.TRACKED) {
                renewSession(event.eventTime, StartReason.EXPLICIT_STOP)
                // Forcing is a deliberate act of the host application; without this the renewal
                // is immediately re-expired when no user interaction happened yet.
                lastUserInteractionNs.set(sdkCore.timeProvider.getDeviceElapsedTimeNanos())
            }
        } else if (event is RumRawEvent.StopSession) {
            stopSession()
        }

        updateSession(event)

        val actualWriter = if (sessionState == State.TRACKED) writer else noOpWriter

        val rumContext = activeView?.getRumContext() ?: getRumContext()

        when (event) {
            is RumRawEvent.AppStartTTIDEvent -> {
                if (sessionState == State.TRACKED) {
                    rumSessionScopeStartupManager?.onTTIDEvent(
                        event = event,
                        datadogContext = datadogContext,
                        writeScope = writeScope,
                        writer = actualWriter,
                        rumContext = rumContext,
                        customAttributes = getCustomAttributes()
                    )
                } else {
                    // can refactor in the future by moving session state check into RumSessionScopeStartupManager
                    sdkCore.getFeature(Feature.PROFILING_FEATURE_NAME)?.sendEvent(
                        ProfilerStopEvent.TTID()
                    )
                }
            }
            is RumRawEvent.AppStartEvent -> {
                if (sessionState == State.TRACKED) {
                    rumSessionScopeStartupManager?.onAppStartEvent(event = event)
                }
            }
            is RumRawEvent.AppStartTTFDEvent -> {
                if (sessionState == State.TRACKED) {
                    rumSessionScopeStartupManager?.onTTFDEvent(
                        event = event,
                        datadogContext = datadogContext,
                        writeScope = writeScope,
                        writer = actualWriter,
                        rumContext = rumContext,
                        customAttributes = getCustomAttributes()
                    )
                }
            }
            is RumRawEvent.SdkInit -> {}
            else -> {
                childScope =
                    childScope?.handleEvent(event, datadogContext, writeScope, actualWriter) as? RumViewManagerScope
            }
        }

        return if (isSessionComplete()) {
            null
        } else {
            this
        }
    }

    override fun getRumContext(): RumContext {
        val parentContext = parentScope.getRumContext()
        return parentContext.copy(
            sessionId = sessionId,
            sessionState = sessionState,
            sessionStartReason = startReason,
            isSessionActive = isActive
        )
    }

    override fun isActive(): Boolean {
        return isActive
    }

    // endregion

    // region Internal

    private fun stopSession() {
        isActive = false
        sessionEndedMetricDispatcher.onSessionStopped(sessionId)
    }

    private fun isSessionComplete(): Boolean {
        return !isActive && childScope == null
    }

    @Suppress("ComplexMethod")
    private fun updateSession(event: RumRawEvent) {
        val nanoTime = sdkCore.timeProvider.getDeviceElapsedTimeNanos()
        val isNewSession = sessionId == RumContext.NULL_UUID

        val timeSinceLastInteractionNs = nanoTime - lastUserInteractionNs.get()
        val isExpired = timeSinceLastInteractionNs >= sessionInactivityNanos
        val timeSinceSessionStartNs = nanoTime - sessionStartNs.get()
        val isTimedOut = timeSinceSessionStartNs >= sessionMaxDurationNanos

        val isInteraction = (event is RumRawEvent.StartView) || (event is RumRawEvent.StartAction)
        val isBackgroundEvent = event.javaClass in RumViewManagerScope.validBackgroundEventTypes
        val isSdkInitInForeground = event is RumRawEvent.SdkInit && event.isAppInForeground
        val isSdkInitInBackground = event is RumRawEvent.SdkInit && !event.isAppInForeground

        // When the session is expired, time-out or stopSession API is called, session ended metric should be sent
        if (isExpired || isTimedOut || isActive.not()) {
            sessionEndedMetricDispatcher.endMetric(sessionId, sdkCore.time.serverTimeOffsetMs)
        }

        if (isInteraction || isSdkInitInForeground) {
            if (isNewSession || isExpired || isTimedOut) {
                val reason = if (isNewSession) {
                    StartReason.USER_APP_LAUNCH
                } else if (isExpired) {
                    StartReason.INACTIVITY_TIMEOUT
                } else {
                    StartReason.MAX_DURATION
                }
                renewSession(event.eventTime, reason)
            }
            lastUserInteractionNs.set(nanoTime)
        } else if (isExpired) {
            if (backgroundTrackingEnabled && (isBackgroundEvent || isSdkInitInBackground)) {
                renewSession(event.eventTime, StartReason.BACKGROUND_LAUNCH)
                lastUserInteractionNs.set(nanoTime)
            } else {
                sessionState = State.EXPIRED
            }
        } else if (isTimedOut) {
            renewSession(event.eventTime, StartReason.MAX_DURATION)
        }

        updateSessionStateForSessionReplay(sessionState, sessionId)
    }

    private fun renewSession(time: Time, reason: StartReason) {
        // FLASHCAT FORK - read the console's rate here, at the one moment a session's fate is
        // decided. A session already running is never redrawn, so a rate arriving mid-session
        // cannot start or stop collecting for someone in the middle of using the app.
        // Order matters: the console's rate first, then the app's own hook. The hook is the last
        // word precisely so an allow-list can keep collecting a visitor the console's rate would
        // drop.
        val drawRate = askBeforeSampling(remoteConfig?.sessionSampleRate() ?: sampleRate)
        val keepSession = forcedSession || random.nextFloat() < drawRate.percent()
        // FLASHCAT FORK - a forced session was not drawn, so it does not report a rate it was drawn
        // at. It reports the rate that describes it: every session like it is kept. Reporting the
        // rate it would have been drawn at instead would have the intake weight one deliberately
        // kept session as the whole population that rate implies - a session forced at a rate of 1
        // would count as a hundred - and would leave nothing to tell it from a lucky draw.
        effectiveSampleRate = if (forcedSession) FORCED_SAMPLE_RATE else drawRate
        childScope?.sampleRate = effectiveSampleRate
        startReason = reason
        sessionState = if (keepSession) State.TRACKED else State.NOT_TRACKED
        sessionId = UUID.randomUUID().toString()
        // FLASHCAT FORK - remember which console configuration this session was drawn under: its
        // events report that version for as long as it lives, so an auditor can recover the exact
        // settings from the console's history. A forced session reports none: it was kept whatever
        // the console said, so naming a version would credit that version with a session it did not
        // decide. This is the reporting the other SDKs use.
        drawnConfiguration = if (forcedSession) {
            null
        } else {
            remoteConfig?.let { config ->
                DrawnConfiguration(version = config.appliedVersion() ?: 0)
            }
        }
        childScope?.drawnConfiguration = drawnConfiguration
        sessionStartNs.set(time.nanoTime)
        rumSessionScopeStartupManager = rumSessionScopeStartupManagerFactory()
        childScope?.renewViewScopes(time)
        if (keepSession) {
            sessionEndedMetricDispatcher.startMetric(
                sessionId = sessionId,
                startReason = reason,
                ntpOffsetAtStartMs = sdkCore.time.serverTimeOffsetMs,
                backgroundEventTracking = backgroundTrackingEnabled
            )
        }
        sessionListener?.onSessionStarted(sessionId, !keepSession)
        // FLASHCAT FORK - the draw is done, so now is the moment to ask again: the response lands
        // in storage for the NEXT session's draw, which is exactly the next-session semantics the
        // console promises. Nothing here waits for the request.
        onSessionDrawn()
    }

    /**
     * FLASHCAT FORK - asks the host application's hook for the rate to draw with. Anything
     * unusable — a throw, a null, a rate outside 0..100 — leaves the incoming rate alone: a mistake
     * in the host application must never take a customer's collection down with it.
     */
    private fun askBeforeSampling(rate: Float): Float {
        val hook = beforeSampling ?: return rate
        val override = try {
            val custom = decodeCustomValues(remoteConfig?.custom())
            hook.sampleRate(BeforeSamplingContext(sessionSampleRate = rate, custom = custom))
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { BEFORE_SAMPLING_THREW_MESSAGE.format(rate) },
                e
            )
            null
        }
        return if (override == null || override.isNaN() || override < 0f || override > MAX_SAMPLE_RATE) {
            rate
        } else {
            override
        }
    }

    private fun updateSessionStateForSessionReplay(state: State, sessionId: String) {
        val keepSession = (state == State.TRACKED)
        sdkCore.getFeature(Feature.SESSION_REPLAY_FEATURE_NAME)?.sendEvent(
            mapOf(
                SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to RUM_SESSION_RENEWED_BUS_MESSAGE,
                RUM_KEEP_SESSION_BUS_MESSAGE_KEY to keepSession,
                // FLASHCAT FORK - a forced session must come out with replay, so Session Replay
                // skips its own draw when this is set.
                RUM_SESSION_FORCED_BUS_MESSAGE_KEY to forcedSession,
                RUM_SESSION_ID_BUS_MESSAGE_KEY to sessionId
            )
        )
    }

    // endregion

    companion object {

        internal const val SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY = "type"
        internal const val RUM_SESSION_RENEWED_BUS_MESSAGE = "rum_session_renewed"
        internal const val RUM_KEEP_SESSION_BUS_MESSAGE_KEY = "keepSession"
        internal const val RUM_SESSION_FORCED_BUS_MESSAGE_KEY = "sessionForced"
        internal const val RUM_SESSION_ID_BUS_MESSAGE_KEY = "sessionId"

        private const val MAX_SAMPLE_RATE = 100f

        // FLASHCAT FORK - what a forced session reports as its rate: it is kept unconditionally,
        // which is what a rate of a hundred means.
        internal const val FORCED_SAMPLE_RATE = 100f

        internal const val BEFORE_SAMPLING_THREW_MESSAGE =
            "The beforeSampling callback failed; drawing this session at %s instead."

        internal val DEFAULT_SESSION_INACTIVITY_NS = TimeUnit.MINUTES.toNanos(15)
        internal val DEFAULT_SESSION_MAX_DURATION_NS = TimeUnit.HOURS.toNanos(4)
    }
}
