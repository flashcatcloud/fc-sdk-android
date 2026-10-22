/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal

import android.app.Application
import android.content.Context
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureEventReceiver
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.feature.StorageBackedFeature
import com.datadog.android.api.net.RequestFactory
import com.datadog.android.api.storage.FeatureStorageConfiguration
import com.datadog.android.core.sampling.RateBasedSampler
import com.datadog.android.core.sampling.Sampler
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.privacy.TrackingConsentProviderCallback
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.MapperTypeWrapper
import com.datadog.android.sessionreplay.SessionReplayInternalCallback
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.TouchPrivacy
import com.datadog.android.sessionreplay.internal.net.BatchesToSegmentsMapper
import com.datadog.android.sessionreplay.internal.net.SegmentRequestFactory
import com.datadog.android.sessionreplay.internal.recorder.NoOpRecorder
import com.datadog.android.sessionreplay.internal.recorder.Recorder
import com.datadog.android.sessionreplay.internal.resources.ResourceDataStoreManager
import com.datadog.android.sessionreplay.internal.resources.ResourceHashesEntryDeserializer
import com.datadog.android.sessionreplay.internal.resources.ResourceHashesEntrySerializer
import com.datadog.android.sessionreplay.internal.storage.NoOpRecordWriter
import com.datadog.android.sessionreplay.internal.storage.RecordWriter
import com.datadog.android.sessionreplay.internal.storage.ResourcesWriter
import com.datadog.android.sessionreplay.internal.storage.SessionReplayRecordWriter
import com.datadog.android.sessionreplay.recorder.OptionSelectorDetector
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Session Replay feature class, which needs to be registered with Datadog SDK instance.
 */
@Suppress("TooManyFunctions")
internal class SessionReplayFeature(
    private val sdkCore: FeatureSdkCore,
    private val customEndpointUrl: String?,
    internal val privacy: SessionReplayPrivacy,
    internal val textAndInputPrivacy: TextAndInputPrivacy,
    internal val touchPrivacy: TouchPrivacy,
    internal val imagePrivacy: ImagePrivacy,
    private val rateBasedSampler: Sampler<Unit>,
    private val startRecordingImmediately: Boolean,
    private val recorderProvider: RecorderProvider
) : StorageBackedFeature, FeatureEventReceiver, TrackingConsentProviderCallback {

    private val currentRumSessionId = AtomicReference<String>()

    @Suppress("LongParameterList")
    internal constructor(
        sdkCore: FeatureSdkCore,
        customEndpointUrl: String?,
        privacy: SessionReplayPrivacy,
        textAndInputPrivacy: TextAndInputPrivacy,
        touchPrivacy: TouchPrivacy,
        touchPrivacyManager: TouchPrivacyManager,
        imagePrivacy: ImagePrivacy,
        customMappers: List<MapperTypeWrapper<*>>,
        customOptionSelectorDetectors: List<OptionSelectorDetector>,
        customDrawableMappers: List<DrawableToColorMapper>,
        sampleRate: Float,
        startRecordingImmediately: Boolean,
        dynamicOptimizationEnabled: Boolean,
        internalCallback: SessionReplayInternalCallback
    ) : this(
        sdkCore,
        customEndpointUrl,
        privacy,
        textAndInputPrivacy,
        touchPrivacy,
        imagePrivacy,
        RateBasedSampler(sampleRate),
        startRecordingImmediately,
        DefaultRecorderProvider(
            sdkCore,
            textAndInputPrivacy,
            imagePrivacy,
            touchPrivacyManager,
            customMappers,
            customOptionSelectorDetectors,
            customDrawableMappers,
            dynamicOptimizationEnabled,
            internalCallback
        )
    )

    private lateinit var appContext: Context

    // should we record the session - a combination of rum sampling, sr sampling
    // and user option.
    private val shouldRecord = AtomicBoolean(false)

    // Indicates the user's intend on recording, it starts with `startRecordingImmediately`
    // in configuration, can be changed by calling start/stop recordings API.
    private val userIntentToRecord = AtomicBoolean(startRecordingImmediately)

    // used to monitor changes to user's intend on recording state
    private val userIntentToRecordChanged = AtomicBoolean(false)

    // are we recording at the moment
    private val isRecording = AtomicBoolean(false)

    // is the current session sampled in
    private val isSessionSampledIn = AtomicBoolean(false)

    // FLASHCAT FORK - the session whose replay is kept only in case it reports an error, for as long
    // as it lives (it stays set once the replay is released), and whether its records are still held.
    private val onErrorReplaySessionId = AtomicReference<String?>()
    private val isReplayWithheld = AtomicBoolean(false)

    internal var sessionReplayRecorder: Recorder = NoOpRecorder()
    internal var dataWriter: RecordWriter = NoOpRecordWriter()
    internal val initialized = AtomicBoolean(false)
    private val rumContextProvider = SessionReplayRumContextProvider()

    // region Feature

    override val name: String = Feature.SESSION_REPLAY_FEATURE_NAME

    override fun onInitialize(appContext: Context) {
        if (appContext !is Application) {
            logMissingApplicationContextError()
            return
        }

        this.appContext = appContext
        sdkCore.setEventReceiver(Feature.SESSION_REPLAY_FEATURE_NAME, this)

        val resourcesFeature = registerResourceFeature(sdkCore)

        val resourceDataStoreManager = ResourceDataStoreManager(
            featureSdkCore = sdkCore,
            resourceHashesSerializer = ResourceHashesEntrySerializer(),
            resourceHashesDeserializer = ResourceHashesEntryDeserializer(internalLogger = sdkCore.internalLogger)
        )

        // FLASHCAT FORK - resources go through the record writer, which holds them with the records
        // of a session whose replay is withheld.
        val recordWriter = createDataWriter(resourcesFeature.dataWriter)
        dataWriter = recordWriter
        sdkCore.setContextUpdateReceiver(rumContextProvider)
        sessionReplayRecorder =
            recorderProvider.provideSessionReplayRecorder(
                resourceDataStoreManager = resourceDataStoreManager,
                resourceWriter = recordWriter,
                recordWriter = dataWriter,
                rumContextProvider = rumContextProvider,
                application = appContext
            )
        sessionReplayRecorder.registerCallbacks()
        initialized.set(true)
        // useContextThread = false, because the read will be on the same caller thread (in a WebViewTracking) during
        // the SDK initialization, so we don't want to block there.
        sdkCore.updateFeatureContext(Feature.SESSION_REPLAY_FEATURE_NAME, useContextThread = false) {
            it[SESSION_REPLAY_SAMPLE_RATE_KEY] = rateBasedSampler.getSampleRate()?.toLong()
            it[SESSION_REPLAY_START_IMMEDIATE_RECORDING_KEY] = startRecordingImmediately
            it[SESSION_REPLAY_TOUCH_PRIVACY_KEY] = touchPrivacy.toString().lowercase(Locale.US)
            it[SESSION_REPLAY_IMAGE_PRIVACY_KEY] = imagePrivacy.toString().lowercase(Locale.US)
            it[SESSION_REPLAY_TEXT_AND_INPUT_PRIVACY_KEY] = textAndInputPrivacy.toString().lowercase(Locale.US)
        }
    }

    override val requestFactory: RequestFactory =
        SegmentRequestFactory(
            customEndpointUrl,
            BatchesToSegmentsMapper(sdkCore.internalLogger)
        )

    override val storageConfiguration: FeatureStorageConfiguration =
        STORAGE_CONFIGURATION

    override fun onStop() {
        stopRecording()
        sdkCore.removeContextUpdateReceiver(rumContextProvider)
        sessionReplayRecorder.unregisterCallbacks()
        sessionReplayRecorder.stopProcessingRecords()
        // FLASHCAT FORK - a replay held for a session that reported its error goes out with the
        // stop, since RUM may be stopped after this feature and could not tell it to any more; the
        // write is queued, so this waits - with a bound - for it to have run.
        val settled = CountDownLatch(1)
        dataWriter.stop { settled.countDown() }
        val done = try {
            settled.await(STOP_WAIT_MS, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
        if (!done) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                { STOP_WAIT_FAILED_MESSAGE }
            )
        }
        dataWriter = NoOpRecordWriter()
        sessionReplayRecorder = NoOpRecorder()
        initialized.set(false)
    }

    // endregion

    // region EventReceiver

    override fun onReceive(event: Any) {
        if (event !is Map<*, *>) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { UNSUPPORTED_EVENT_TYPE.format(Locale.US, event::class.java.canonicalName) }
            )
            return
        }

        if (!checkIfInitialized()) {
            return
        }

        handleRumSession(event)
    }

    // endregion

    // region TrackingConsentProviderCallback

    // FLASHCAT FORK - a replay held in memory is not in the storage consent governs: what was held
    // under the consent now withdrawn is dropped here, whether or not a record follows to see it.
    override fun onConsentUpdated(previousConsent: TrackingConsent, newConsent: TrackingConsent) {
        if (newConsent == TrackingConsent.NOT_GRANTED) dataWriter.dropForConsent()
    }

    // endregion

    // region Manual Recording

    internal fun manuallyStopRecording() {
        if (userIntentToRecord.compareAndSet(true, false)) {
            userIntentToRecordChanged.set(true)
        }
    }

    internal fun manuallyStartRecording() {
        if (userIntentToRecord.compareAndSet(false, true)) {
            userIntentToRecordChanged.set(true)
        }
    }

    // endregion

    // region Internal

    // FLASHCAT FORK - serialized: a session announces itself from the RUM thread, while what became
    // of a withheld one arrives from the storage thread, and the two must not interleave.
    @Synchronized
    private fun handleRumSession(sessionMetadata: Map<*, *>) {
        if (sessionMetadata[SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY] ==
            RUM_SESSION_RENEWED_BUS_MESSAGE
        ) {
            parseSessionMetadata(sessionMetadata)
                ?.let { sessionData ->
                    val alreadySeenSession = currentRumSessionId.get() == sessionData.sessionId
                    val forceSampling = sessionData.forced && !isSessionSampledIn.get()
                    // FLASHCAT FORK - a held replay is released once the session's events are (or by
                    // forcing).
                    val released = isReplayWithheld.get() && (sessionData.released || sessionData.forced)
                    if (!alreadySeenSession || forceSampling || released || userIntentToRecordChanged.get()) {
                        applySampling(alreadySeenSession, sessionData.forced)
                        val withhold = shouldWithhold(sessionData)
                        modifyShouldRecordState(sessionData, withhold)
                        updateWithholding(sessionData.sessionId, withhold)
                        handleRecording(sessionData)
                    }
                }
        } else if (sessionMetadata[SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY] == RUM_SESSION_RELEASED_BUS_MESSAGE) {
            // FLASHCAT FORK - the session's events have just been released: the replay held for it
            // goes out now. For the session still current, recording carries on as for any collected
            // session; a session that ended before its word arrived only has what it held to send.
            val sessionId = sessionMetadata[RUM_SESSION_ID_BUS_MESSAGE_KEY] as? String ?: return
            if (isReplayWithheld.get() && currentRumSessionId.get() == sessionId) {
                updateWithholding(sessionId, withhold = false)
            } else {
                dataWriter.release(sessionId)
            }
        } else if (sessionMetadata[SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY] == RUM_SESSION_ERRORED_BUS_MESSAGE) {
            // FLASHCAT FORK - the session reported its error: its replay goes out once its events
            // do, or with the stop if that comes first.
            (sessionMetadata[RUM_SESSION_ID_BUS_MESSAGE_KEY] as? String)?.let { dataWriter.expectRelease(it) }
        } else if (sessionMetadata[SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY] == RUM_SESSION_DISCARDED_BUS_MESSAGE) {
            // FLASHCAT FORK - the session ended without an error: what was held for it goes.
            (sessionMetadata[RUM_SESSION_ID_BUS_MESSAGE_KEY] as? String)?.let { dataWriter.discard(it) }
        } else {
            sdkCore.internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                {
                    UNKNOWN_EVENT_TYPE_PROPERTY_VALUE.format(
                        Locale.US,
                        sessionMetadata[SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY]
                    )
                }
            )
        }
    }

    private data class SessionData(
        val keepSession: Boolean,
        val sessionId: String,
        val forced: Boolean,
        // FLASHCAT FORK - see `RumSessionScope.updateSessionStateForSessionReplay`.
        val eventsOnError: Boolean,
        val replayOnError: Boolean,
        val released: Boolean
    )

    private fun parseSessionMetadata(sessionMetadata: Map<*, *>): SessionData? {
        val keepSession = sessionMetadata[RUM_KEEP_SESSION_BUS_MESSAGE_KEY] as? Boolean
        val sessionId = sessionMetadata[RUM_SESSION_ID_BUS_MESSAGE_KEY] as? String

        if (keepSession == null || sessionId == null) {
            logEventMissingMandatoryFieldsError()
            return null
        }

        val forced = sessionMetadata[RUM_SESSION_FORCED_BUS_MESSAGE_KEY] as? Boolean ?: false
        return SessionData(
            keepSession = keepSession,
            sessionId = sessionId,
            forced = forced,
            eventsOnError = sessionMetadata[RUM_SESSION_ON_ERROR_BUS_MESSAGE_KEY] as? Boolean ?: false,
            replayOnError = sessionMetadata[RUM_REPLAY_ON_ERROR_BUS_MESSAGE_KEY] as? Boolean ?: false,
            released = sessionMetadata[RUM_SESSION_RELEASED_BUS_MESSAGE_KEY] as? Boolean ?: false
        )
    }

    private fun applySampling(alreadySeenSession: Boolean, forced: Boolean) {
        if (forced) {
            isSessionSampledIn.set(true)
        } else if (!alreadySeenSession) {
            isSessionSampledIn.set(rateBasedSampler.sample(Unit))
        }
    }

    /**
     * FLASHCAT FORK - whether this session's records are held until it reports an error. Two
     * sessions are: a collected one whose replay the rate missed while the replay switch is on, and
     * one whose events are themselves held - its replay waits with them whichever way the replay
     * draw went, because until the events are released the session does not exist at the intake and
     * a replay uploaded before then would have nothing to attach to.
     */
    private fun shouldWithhold(sessionData: SessionData): Boolean {
        if (sessionData.released || sessionData.forced) return false
        return if (sessionData.eventsOnError) {
            isSessionSampledIn.get() || sessionData.replayOnError
        } else {
            sessionData.keepSession && !isSessionSampledIn.get() && sessionData.replayOnError
        }
    }

    private fun updateWithholding(sessionId: String, withhold: Boolean) {
        if (withhold) {
            onErrorReplaySessionId.set(sessionId)
            dataWriter.withhold(sessionId)
        } else {
            // Releases what this session held, if it held anything; what another session still
            // holds is kept for RUM's word or thrown away, see the writer.
            dataWriter.stopWithholding(sessionId)
        }
        val wasWithheld = isReplayWithheld.getAndSet(withhold)
        val onErrorSessionId = onErrorReplaySessionId.get()
        // Nothing ever withheld: the context stays exactly as it is for everyone who did not opt in.
        if (!withhold && !wasWithheld && onErrorSessionId == null) return
        sdkCore.updateFeatureContext(Feature.SESSION_REPLAY_FEATURE_NAME) {
            it[SESSION_REPLAY_ON_ERROR_SESSION_KEY] = onErrorSessionId?.takeIf { id -> id == sessionId }
            it[SESSION_REPLAY_WITHHELD_KEY] = withhold
        }
    }

    private fun modifyShouldRecordState(sessionData: SessionData, withhold: Boolean) {
        // A replay kept on error stays eligible once released, whether or not the rate drew it.
        val keptOnError = onErrorReplaySessionId.get() == sessionData.sessionId
        val isSessionEligible = withhold ||
            (sessionData.keepSession && (isSessionSampledIn.get() || keptOnError))
        if (isSessionEligible) {
            shouldRecord.set(userIntentToRecord.get())
        } else {
            shouldRecord.set(false)
            if (!sessionData.keepSession) {
                logNotKeptMessage()
            } else {
                logSampledOutMessage()
            }
        }
    }

    private fun logMissingApplicationContextError() {
        sdkCore.internalLogger.log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            { REQUIRES_APPLICATION_CONTEXT_WARN_MESSAGE }
        )
    }

    private fun logEventMissingMandatoryFieldsError() {
        sdkCore.internalLogger.log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            { EVENT_MISSING_MANDATORY_FIELDS }
        )
    }

    private fun logNotKeptMessage() {
        sdkCore.internalLogger.log(
            InternalLogger.Level.INFO,
            InternalLogger.Target.USER,
            { SESSION_NOT_KEPT_MESSAGE }
        )
    }

    private fun logSampledOutMessage() {
        sdkCore.internalLogger.log(
            InternalLogger.Level.INFO,
            InternalLogger.Target.USER,
            { SESSION_SAMPLED_OUT_MESSAGE }
        )
    }

    private fun logNotInitializedError() {
        sdkCore.internalLogger.log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            { CANNOT_START_RECORDING_NOT_INITIALIZED }
        )
    }

    private fun handleRecording(sessionData: SessionData) {
        if (shouldRecord.get()) {
            startRecording()
        } else {
            stopRecording()
        }

        userIntentToRecordChanged.set(false)
        currentRumSessionId.set(sessionData.sessionId)
    }

    private fun checkIfInitialized(): Boolean {
        if (!initialized.get()) {
            logNotInitializedError()
            return false
        }
        return true
    }

    /**
     * Resumes the replay recorder.
     */
    internal fun startRecording() {
        // Check initialization again so we don't forget to do it when this method is made public
        if (checkIfInitialized() && !isRecording.getAndSet(true)) {
            sdkCore.updateFeatureContext(Feature.SESSION_REPLAY_FEATURE_NAME) {
                it[SESSION_REPLAY_ENABLED_KEY] = true
            }
            sessionReplayRecorder.resumeRecorders()
        }
    }

    private fun createDataWriter(resourcesWriter: ResourcesWriter): SessionReplayRecordWriter {
        val recordCallback = SessionReplayRecordCallback(sdkCore)
        return SessionReplayRecordWriter(sdkCore, recordCallback, resourcesWriter) { resourceIds ->
            sessionReplayRecorder.forgetResources(resourceIds)
        }
    }

    /**
     * Stops the replay recorder.
     */
    internal fun stopRecording() {
        if (isRecording.getAndSet(false)) {
            sdkCore.updateFeatureContext(Feature.SESSION_REPLAY_FEATURE_NAME) {
                it[SESSION_REPLAY_ENABLED_KEY] = false
            }
            sessionReplayRecorder.stopRecorders()
        }
    }

    // endregion

    // region resourcesFeature

    private fun registerResourceFeature(sdkCore: SdkCore): ResourcesFeature {
        val resourcesFeature = ResourcesFeature(
            sdkCore = sdkCore as FeatureSdkCore,
            customEndpointUrl = customEndpointUrl
        )
        sdkCore.registerFeature(resourcesFeature)

        return resourcesFeature
    }

    // endregion

    internal companion object {

        /**
         * Session Replay storage configuration with the following parameters:
         * max item size = 10 MB,
         * max items per batch = 500,
         * max batch size = 10 MB, SR intake batch limit is 10MB
         * old batch threshold = 5 hours.
         */
        internal val STORAGE_CONFIGURATION: FeatureStorageConfiguration =
            FeatureStorageConfiguration.DEFAULT.copy(
                maxItemSize = 10 * 1024 * 1024,
                maxBatchSize = 10 * 1024 * 1024,
                oldBatchThreshold = 5L * 60L * 60L * 1000L
            )

        internal const val REQUIRES_APPLICATION_CONTEXT_WARN_MESSAGE = "Session Replay could not " +
            "be initialized without the Application context."
        internal const val SESSION_SAMPLED_OUT_MESSAGE = "This session was sampled out from" +
            " recording. No replay will be provided for it."
        internal const val SESSION_NOT_KEPT_MESSAGE =
            "This session was not kept. No replay will be provided for it."
        internal const val UNSUPPORTED_EVENT_TYPE =
            "Session Replay feature receive an event of unsupported type=%s."
        internal const val UNKNOWN_EVENT_TYPE_PROPERTY_VALUE =
            "Session Replay feature received an event with unknown value of \"type\" property=%s."
        internal const val EVENT_MISSING_MANDATORY_FIELDS = "Session Replay feature received an " +
            "event where one or more mandatory (keepSession) fields" +
            " are either missing or have wrong type."
        internal const val CANNOT_START_RECORDING_NOT_INITIALIZED =
            "Cannot start session recording, because Session Replay feature is not initialized."
        const val SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY = "type"
        const val RUM_SESSION_RENEWED_BUS_MESSAGE = "rum_session_renewed"
        const val RUM_KEEP_SESSION_BUS_MESSAGE_KEY = "keepSession"
        const val RUM_SESSION_FORCED_BUS_MESSAGE_KEY = "sessionForced"
        const val RUM_SESSION_ID_BUS_MESSAGE_KEY = "sessionId"
        const val RUM_SESSION_ON_ERROR_BUS_MESSAGE_KEY = "sessionOnError"
        const val RUM_REPLAY_ON_ERROR_BUS_MESSAGE_KEY = "sessionReplayOnError"
        const val RUM_SESSION_RELEASED_BUS_MESSAGE_KEY = "sessionReleased"
        const val RUM_SESSION_RELEASED_BUS_MESSAGE = "rum_session_released"
        const val RUM_SESSION_DISCARDED_BUS_MESSAGE = "rum_session_discarded"
        const val RUM_SESSION_ERRORED_BUS_MESSAGE = "rum_session_errored"
        private const val STOP_WAIT_MS = 2_000L
        internal const val STOP_WAIT_FAILED_MESSAGE =
            "Could not wait for the held replay to be settled before Session Replay stopped."

        // FLASHCAT FORK - read by RUM to mark view events: the current session when its replay is
        // kept only on error, and whether its records are still held.
        internal const val SESSION_REPLAY_ON_ERROR_SESSION_KEY = "session_replay_on_error_session_id"
        internal const val SESSION_REPLAY_WITHHELD_KEY = "session_replay_withheld"
        internal const val SESSION_REPLAY_SAMPLE_RATE_KEY = "session_replay_sample_rate"
        internal const val SESSION_REPLAY_TEXT_AND_INPUT_PRIVACY_KEY = "session_replay_text_and_input_privacy"
        internal const val SESSION_REPLAY_IMAGE_PRIVACY_KEY = "session_replay_image_privacy"
        internal const val SESSION_REPLAY_TOUCH_PRIVACY_KEY = "session_replay_touch_privacy"
        internal const val SESSION_REPLAY_START_IMMEDIATE_RECORDING_KEY =
            "session_replay_start_immediate_recording"
        internal const val SESSION_REPLAY_ENABLED_KEY =
            "session_replay_is_enabled"
    }
}
