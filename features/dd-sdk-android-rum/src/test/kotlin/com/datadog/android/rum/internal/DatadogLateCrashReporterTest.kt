/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.context.UserInfo
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.feature.event.ThreadDump
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.assertj.ErrorEventAssert
import com.datadog.android.rum.assertj.ViewEventAssert
import com.datadog.android.rum.internal.anr.ANRDetectorRunnable
import com.datadog.android.rum.internal.anr.ANRException
import com.datadog.android.rum.internal.anr.AndroidTraceParser
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.RumDataWriter
import com.datadog.android.rum.internal.domain.scope.toErrorSchemaType
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.rum.utils.verifyLog
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.isNull
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.IOException
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogLateCrashReporterTest {

    private lateinit var testedHandler: LateCrashReporter

    @Mock
    lateinit var mockSdkCore: InternalSdkCore

    @Mock
    lateinit var mockRumFeatureScope: FeatureScope

    @Mock
    lateinit var mockRumWriter: DataWriter<Any>

    @Mock
    lateinit var mockRumEventDeserializer: Deserializer<JsonObject, Any>

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    @Mock
    lateinit var mockEventWriteScope: EventWriteScope

    @Mock
    lateinit var mockAndroidTraceParser: AndroidTraceParser

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @LongForgery(min = 0L)
    var fakeCurrentTimeMs: Long = 0L

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    private var fakeNewSession: DatadogLateCrashReporter.NewSession? = null
    private var drawCount = 0

    @BeforeEach
    fun `set up`(forge: Forge) {
        // Views are dated on the server clock, crashes on the device clock: a sane device clock.
        fakeDatadogContext = fakeDatadogContext.copy(
            time = fakeDatadogContext.time.copy(serverTimeOffsetMs = forge.aLong(min = -60_000L, max = 60_000L))
        )
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockTimeProvider.getDeviceTimestampMillis()) doReturn fakeCurrentTimeMs
        whenever(mockSdkCore.timeProvider) doReturn mockTimeProvider
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope

        whenever(mockEventWriteScope.invoke(any())) doAnswer {
            val callback = it.getArgument<(EventBatchWriter) -> Unit>(0)
            callback.invoke(mockEventBatchWriter)
        }
        whenever(mockRumFeatureScope.withWriteContext(any(), any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventWriteScope) -> Unit>(it.arguments.lastIndex)
            callback.invoke(fakeDatadogContext, mockEventWriteScope)
        }

        testedHandler = DatadogLateCrashReporter(
            sdkCore = mockSdkCore,
            rumEventDeserializer = mockRumEventDeserializer,
            androidTraceParser = mockAndroidTraceParser,
            drawNewSession = {
                drawCount++
                fakeNewSession
            }
        )
    }

    // region handleNdkCrashEvent

    @Test
    fun `M send RUM view+error W handleNdkCrashEvent()`(
        @StringForgery crashMessage: String,
        @LongForgery(min = 0, max = 1_000) fakeCrashDelayMs: Long,
        @LongForgery(min = 1) fakeTimeSinceAppStartMs: Long,
        @StringForgery fakeSignalName: String,
        @StringForgery fakeStacktrace: String,
        @Forgery viewEvent: ViewEvent,
        @Forgery fakeUserInfo: UserInfo,
        forge: Forge
    ) {
        // Given
        val fakeViewEvent = viewEvent.copy(
            date = fakeCurrentTimeMs - forge.aLong(
                min = 0L,
                max = DatadogLateCrashReporter.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD - 1000
            ),
            usr = ViewEvent.Usr(
                id = fakeUserInfo.id,
                name = fakeUserInfo.name,
                email = fakeUserInfo.email,
                anonymousId = fakeUserInfo.anonymousId,
                additionalProperties = fakeUserInfo.additionalProperties.toMutableMap()
            )
        )
        // The crash follows the last view the crashed run wrote.
        val fakeTimestamp = fakeViewEvent.date + fakeCrashDelayMs
        val fakeServerOffset =
            forge.aLong(min = -60_000L, max = 60_000L)
        fakeDatadogContext = fakeDatadogContext.copy(
            time = fakeDatadogContext.time.copy(
                serverTimeOffsetMs = fakeServerOffset
            )
        )
        val fakeViewEventJson = fakeViewEvent.toJson().asJsonObject

        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson))
            .doReturn(fakeViewEvent)

        val fakeEvent = mapOf(
            "timestamp" to fakeTimestamp,
            "timeSinceAppStartMs" to fakeTimeSinceAppStartMs,
            "signalName" to fakeSignalName,
            "stacktrace" to fakeStacktrace,
            "message" to crashMessage,
            "lastViewEvent" to fakeViewEventJson
        )

        // When
        testedHandler.handleNdkCrashEvent(fakeEvent, mockRumWriter)

        // Then
        argumentCaptor<Any> {
            verify(mockRumWriter, times(2)).write(eq(mockEventBatchWriter), capture(), eq(EventType.CRASH))

            ErrorEventAssert.assertThat(firstValue as ErrorEvent)
                .hasErrorId()
                .hasApplicationId(fakeViewEvent.application.id)
                .hasSessionId(fakeViewEvent.session.id)
                .hasView(
                    fakeViewEvent.view.id,
                    fakeViewEvent.view.name,
                    fakeViewEvent.view.url
                )
                .hasBuildId(fakeDatadogContext.appBuildId)
                .hasMessage(crashMessage)
                .hasStackTrace(fakeStacktrace)
                .isCrash(true)
                .hasErrorSource(RumErrorSource.SOURCE)
                .hasErrorSourceType(ErrorEvent.SourceType.NDK)
                .hasTimestamp(fakeTimestamp + fakeServerOffset)
                .hasUserInfo(
                    UserInfo(
                        fakeViewEvent.usr?.anonymousId,
                        fakeViewEvent.usr?.id,
                        fakeViewEvent.usr?.name,
                        fakeViewEvent.usr?.email,
                        fakeViewEvent.usr?.additionalProperties.orEmpty()
                    )
                )
                .hasErrorType(fakeSignalName)
                .hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                .hasTimeSinceAppStart(fakeTimeSinceAppStartMs)
                .hasDeviceInfo(
                    fakeDatadogContext.deviceInfo.deviceName,
                    fakeDatadogContext.deviceInfo.deviceModel,
                    fakeDatadogContext.deviceInfo.deviceBrand,
                    fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                    fakeDatadogContext.deviceInfo.architecture
                )
                .hasOsInfo(
                    fakeDatadogContext.deviceInfo.osName,
                    fakeDatadogContext.deviceInfo.osVersion,
                    fakeDatadogContext.deviceInfo.osMajorVersion
                )

            ViewEventAssert.assertThat(secondValue as ViewEvent)
                .hasVersion(fakeViewEvent.dd.documentVersion + 1)
                .hasCrashCount((fakeViewEvent.view.crash?.count ?: 0) + 1)
                .isActive(false)
        }
    }

    @Test
    fun `M write the crash under the beacon's id W handleNdkCrashEvent() { crash beacon record }`(
        @LongForgery(min = 0, max = 1_000) fakeCrashDelayMs: Long,
        @Forgery viewEvent: ViewEvent
    ) {
        // Given
        val fakeViewEvent = viewEvent.copy(date = fakeCurrentTimeMs)
        // The crash follows the last view the crashed run wrote.
        val fakeTimestamp = fakeViewEvent.date + fakeCrashDelayMs
        val fakeViewEventJson = fakeViewEvent.toJson().asJsonObject
        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson)).doReturn(fakeViewEvent)
        val fakeCrashId = "6a1f0c2e-3b4d-4e5f-8a9b-0c1d2e3f4a5b"

        // When
        testedHandler.handleNdkCrashEvent(
            mapOf(
                "timestamp" to fakeTimestamp,
                "timeSinceAppStartMs" to 4_000L,
                "signalName" to "SIGSEGV",
                "stacktrace" to "stack",
                "message" to "NDK crash detected with signal: SIGSEGV",
                "lastViewEvent" to fakeViewEventJson,
                "crashId" to fakeCrashId,
                "beaconStatus" to "sent",
                "beaconDuration" to 105L,
                "crashDate" to 1_700_000_000_123L
            ),
            mockRumWriter
        )

        // Then
        argumentCaptor<Any> {
            verify(mockRumWriter, atLeastOnce()).write(eq(mockEventBatchWriter), capture(), eq(EventType.CRASH))
            val error = allValues.filterIsInstance<ErrorEvent>().single()
            assertThat(error.error.id).isEqualTo(fakeCrashId)
            assertThat(error.error.beacon).isEqualTo(ErrorEvent.Beacon(status = "sent", duration = 105L))
            assertThat(error.error.timeSinceAppStart).isEqualTo(4_000L)
            assertThat(error.date).isEqualTo(1_700_000_000_123L)
        }
    }

    @Test
    fun `M report the view of a session kept on error however old W handleNdkCrashEvent()`(
        @StringForgery crashMessage: String,
        @LongForgery(min = 0, max = 1_000) fakeCrashDelayMs: Long,
        @StringForgery fakeSignalName: String,
        @StringForgery fakeStacktrace: String,
        @Forgery viewEvent: ViewEvent,
        forge: Forge
    ) {
        // Given - a withheld session uploaded nothing: this view is the only one the intake will get
        val fakeViewEvent = viewEvent.copy(
            date = fakeCurrentTimeMs - forge.aLong(
                min = DatadogLateCrashReporter.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD + 1,
                max = DatadogLateCrashReporter.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD * 4
            ),
            session = viewEvent.session.copy(sampledForError = true)
        )
        // The crash follows the last view the crashed run wrote.
        val fakeTimestamp = fakeViewEvent.date + fakeCrashDelayMs
        val fakeViewEventJson = fakeViewEvent.toJson().asJsonObject
        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson)) doReturn fakeViewEvent
        val fakeEvent = mapOf(
            "timestamp" to fakeTimestamp,
            "signalName" to fakeSignalName,
            "stacktrace" to fakeStacktrace,
            "message" to crashMessage,
            "lastViewEvent" to fakeViewEventJson
        )

        // When
        testedHandler.handleNdkCrashEvent(fakeEvent, mockRumWriter)

        // Then - the crash and the view, unlike a collected session's view this old
        argumentCaptor<Any> {
            verify(mockRumWriter, times(2)).write(eq(mockEventBatchWriter), capture(), eq(EventType.CRASH))
            assertThat(firstValue).isInstanceOf(ErrorEvent::class.java)
            assertThat((secondValue as ViewEvent).session.sampledForError).isTrue
        }
    }

    @Test
    fun `M report the crash of a session kept on error W handleNdkCrashEvent()`(
        @StringForgery crashMessage: String,
        @LongForgery(min = 0, max = 1_000) fakeCrashDelayMs: Long,
        @StringForgery fakeSignalName: String,
        @StringForgery fakeStacktrace: String,
        @Forgery viewEvent: ViewEvent
    ) {
        // Given - the last view a withheld session wrote locally: rate 0 and the marker
        val fakeViewEvent = viewEvent.copy(
            date = fakeCurrentTimeMs - 1000,
            session = viewEvent.session.copy(sampledForError = true),
            dd = viewEvent.dd.copy(configuration = ViewEvent.Configuration(sessionSampleRate = 0f))
        )
        // The crash follows the last view the crashed run wrote.
        val fakeTimestamp = fakeViewEvent.date + fakeCrashDelayMs
        val fakeViewEventJson = fakeViewEvent.toJson().asJsonObject
        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson)) doReturn fakeViewEvent
        val fakeEvent = mapOf(
            "timestamp" to fakeTimestamp,
            "signalName" to fakeSignalName,
            "stacktrace" to fakeStacktrace,
            "message" to crashMessage,
            "lastViewEvent" to fakeViewEventJson
        )

        // When
        testedHandler.handleNdkCrashEvent(fakeEvent, mockRumWriter)

        // Then - the crash is reported, standing for itself, and the view keeps the marker
        argumentCaptor<Any> {
            verify(mockRumWriter, times(2)).write(eq(mockEventBatchWriter), capture(), eq(EventType.CRASH))
            val error = firstValue as ErrorEvent
            assertThat(error.session.id).isEqualTo(fakeViewEvent.session.id)
            assertThat(error.dd.configuration?.sessionSampleRate).isEqualTo(0f)
            val view = secondValue as ViewEvent
            assertThat(view.session.sampledForError).isTrue
            assertThat(view.dd.configuration?.sessionSampleRate).isEqualTo(0f)
            assertThat(view.view.crash?.count).isEqualTo((fakeViewEvent.view.crash?.count ?: 0) + 1)
        }
    }

    @Test
    fun `M write the view ahead of the crash W handleNdkCrashEvent() {session kept on error}`(
        @StringForgery crashMessage: String,
        @StringForgery fakeSignalName: String,
        @StringForgery fakeStacktrace: String,
        @Forgery viewEvent: ViewEvent
    ) {
        // Given - the intake builds the session out of the first view it sees
        val mockRumDataWriter = mock<RumDataWriter>()
        val serializedError = RawBatchEvent(data = "crash".toByteArray())
        whenever(mockRumDataWriter.serialize(any())) doReturn serializedError
        whenever(mockRumDataWriter.write(any(), any(), any())) doReturn true
        val fakeEvent =
            sessionKeptOnErrorNdkCrash(viewEvent, crashMessage, fakeSignalName, fakeStacktrace)

        // When
        testedHandler.handleNdkCrashEvent(fakeEvent, mockRumDataWriter)

        // Then
        inOrder(mockRumDataWriter) {
            verify(mockRumDataWriter).serialize(any<ErrorEvent>())
            verify(mockRumDataWriter).write(eq(mockEventBatchWriter), any<ViewEvent>(), eq(EventType.CRASH))
            verify(mockRumDataWriter).writeSerialized(mockEventBatchWriter, serializedError, EventType.CRASH)
        }
    }

    @Test
    fun `M write nothing W handleNdkCrashEvent() {session kept on error, crash not serializable}`(
        @StringForgery crashMessage: String,
        @StringForgery fakeSignalName: String,
        @StringForgery fakeStacktrace: String,
        @Forgery viewEvent: ViewEvent
    ) {
        // Given - the view alone would be a kept session without the error it was kept for
        val mockRumDataWriter = mock<RumDataWriter>()
        whenever(mockRumDataWriter.serialize(any())) doReturn null
        val fakeEvent =
            sessionKeptOnErrorNdkCrash(viewEvent, crashMessage, fakeSignalName, fakeStacktrace)

        // When
        testedHandler.handleNdkCrashEvent(fakeEvent, mockRumDataWriter)

        // Then
        verify(mockRumDataWriter, never()).write(any(), any(), any())
        verify(mockRumDataWriter, never()).writeSerialized(any(), any(), any())
    }

    private fun sessionKeptOnErrorNdkCrash(
        viewEvent: ViewEvent,
        crashMessage: String,
        fakeSignalName: String,
        fakeStacktrace: String
    ): Map<String, Any> {
        val fakeViewEvent = viewEvent.copy(
            date = fakeCurrentTimeMs - 1000,
            session = viewEvent.session.copy(sampledForError = true)
        )
        val fakeViewEventJson = fakeViewEvent.toJson().asJsonObject
        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson)) doReturn fakeViewEvent
        return mapOf(
            "timestamp" to fakeViewEvent.date + 1,
            "signalName" to fakeSignalName,
            "stacktrace" to fakeStacktrace,
            "message" to crashMessage,
            "lastViewEvent" to fakeViewEventJson
        )
    }

    @Test
    fun `M send RUM view+error W handleNdkCrashEvent() {source_type set}`(
        @StringForgery crashMessage: String,
        @LongForgery(min = 0, max = 1_000) fakeCrashDelayMs: Long,
        @LongForgery(min = 1) fakeTimeSinceAppStartMs: Long,
        @StringForgery fakeSignalName: String,
        @StringForgery fakeStacktrace: String,
        @Forgery viewEvent: ViewEvent,
        @Forgery fakeUserInfo: UserInfo,
        forge: Forge
    ) {
        // Given
        val fakeViewEvent = viewEvent.copy(
            date = fakeCurrentTimeMs - forge.aLong(
                min = 0L,
                max = DatadogLateCrashReporter.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD - 1000
            ),
            usr = ViewEvent.Usr(
                id = fakeUserInfo.id,
                name = fakeUserInfo.name,
                email = fakeUserInfo.email,
                anonymousId = fakeUserInfo.anonymousId,
                additionalProperties = fakeUserInfo.additionalProperties.toMutableMap()
            )
        )
        // The crash follows the last view the crashed run wrote.
        val fakeTimestamp = fakeViewEvent.date + fakeCrashDelayMs
        val fakeServerOffset =
            forge.aLong(min = -60_000L, max = 60_000L)
        fakeDatadogContext = fakeDatadogContext.copy(
            time = fakeDatadogContext.time.copy(
                serverTimeOffsetMs = fakeServerOffset
            )
        )
        val fakeViewEventJson = fakeViewEvent.toJson().asJsonObject

        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson))
            .doReturn(fakeViewEvent)

        val fakeEvent = mapOf(
            "sourceType" to "ndk+il2cpp",
            "timestamp" to fakeTimestamp,
            "timeSinceAppStartMs" to fakeTimeSinceAppStartMs,
            "signalName" to fakeSignalName,
            "stacktrace" to fakeStacktrace,
            "message" to crashMessage,
            "lastViewEvent" to fakeViewEventJson
        )

        // When
        testedHandler.handleNdkCrashEvent(fakeEvent, mockRumWriter)

        // Then
        argumentCaptor<Any> {
            verify(mockRumWriter, times(2)).write(eq(mockEventBatchWriter), capture(), eq(EventType.CRASH))

            ErrorEventAssert.assertThat(firstValue as ErrorEvent)
                .hasErrorId()
                .hasApplicationId(fakeViewEvent.application.id)
                .hasSessionId(fakeViewEvent.session.id)
                .hasView(
                    fakeViewEvent.view.id,
                    fakeViewEvent.view.name,
                    fakeViewEvent.view.url
                )
                .hasBuildId(fakeDatadogContext.appBuildId)
                .hasMessage(crashMessage)
                .hasStackTrace(fakeStacktrace)
                .isCrash(true)
                .hasErrorSource(RumErrorSource.SOURCE)
                .hasErrorSourceType(ErrorEvent.SourceType.NDK_IL2CPP)
                .hasTimestamp(fakeTimestamp + fakeServerOffset)
                .hasUserInfo(
                    UserInfo(
                        fakeViewEvent.usr?.anonymousId,
                        fakeViewEvent.usr?.id,
                        fakeViewEvent.usr?.name,
                        fakeViewEvent.usr?.email,
                        fakeViewEvent.usr?.additionalProperties.orEmpty()
                    )
                )
                .hasErrorType(fakeSignalName)
                .hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                .hasTimeSinceAppStart(fakeTimeSinceAppStartMs)
                .hasDeviceInfo(
                    fakeDatadogContext.deviceInfo.deviceName,
                    fakeDatadogContext.deviceInfo.deviceModel,
                    fakeDatadogContext.deviceInfo.deviceBrand,
                    fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                    fakeDatadogContext.deviceInfo.architecture
                )
                .hasOsInfo(
                    fakeDatadogContext.deviceInfo.osName,
                    fakeDatadogContext.deviceInfo.osVersion,
                    fakeDatadogContext.deviceInfo.osMajorVersion
                )

            ViewEventAssert.assertThat(secondValue as ViewEvent)
                .hasVersion(fakeViewEvent.dd.documentVersion + 1)
                .hasCrashCount((fakeViewEvent.view.crash?.count ?: 0) + 1)
                .isActive(false)
        }
    }

    @Test
    fun `M send RUM view+error W handleNdkCrashEvent() {invalid source_type set}`(
        @StringForgery crashMessage: String,
        @LongForgery(min = 0, max = 1_000) fakeCrashDelayMs: Long,
        @LongForgery(min = 1) fakeTimeSinceAppStartMs: Long,
        @StringForgery fakeSignalName: String,
        @StringForgery fakeStacktrace: String,
        @Forgery viewEvent: ViewEvent,
        @Forgery fakeUserInfo: UserInfo,
        forge: Forge
    ) {
        // Given
        val fakeViewEvent = viewEvent.copy(
            date = fakeCurrentTimeMs - forge.aLong(
                min = 0L,
                max = DatadogLateCrashReporter.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD - 1000
            ),
            usr = ViewEvent.Usr(
                id = fakeUserInfo.id,
                name = fakeUserInfo.name,
                email = fakeUserInfo.email,
                anonymousId = fakeUserInfo.anonymousId,
                additionalProperties = fakeUserInfo.additionalProperties.toMutableMap()
            )
        )
        // The crash follows the last view the crashed run wrote.
        val fakeTimestamp = fakeViewEvent.date + fakeCrashDelayMs
        val fakeServerOffset =
            forge.aLong(min = -60_000L, max = 60_000L)
        fakeDatadogContext = fakeDatadogContext.copy(
            time = fakeDatadogContext.time.copy(
                serverTimeOffsetMs = fakeServerOffset
            )
        )
        val fakeViewEventJson = fakeViewEvent.toJson().asJsonObject

        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson))
            .doReturn(fakeViewEvent)

        val fakeEvent = mapOf(
            "sourceType" to "invalid",
            "timestamp" to fakeTimestamp,
            "fakeTimeSinceAppStartMs" to fakeTimeSinceAppStartMs,
            "signalName" to fakeSignalName,
            "stacktrace" to fakeStacktrace,
            "message" to crashMessage,
            "lastViewEvent" to fakeViewEventJson
        )

        // When
        testedHandler.handleNdkCrashEvent(fakeEvent, mockRumWriter)

        // Then
        argumentCaptor<Any> {
            verify(mockRumWriter, times(2)).write(eq(mockEventBatchWriter), capture(), eq(EventType.CRASH))

            ErrorEventAssert.assertThat(firstValue as ErrorEvent)
                .hasErrorId()
                .hasErrorSourceType(ErrorEvent.SourceType.NDK)
        }
    }

    @Test
    fun `M send RUM view+error W handleNdkCrashEvent() {view without usr}`(
        @StringForgery crashMessage: String,
        @LongForgery(min = 0, max = 1_000) fakeCrashDelayMs: Long,
        @LongForgery(min = 1) fakeTimeSinceAppStartMs: Long,
        @StringForgery fakeSignalName: String,
        @StringForgery fakeStacktrace: String,
        @Forgery viewEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val fakeViewEvent = viewEvent.copy(
            date = fakeCurrentTimeMs - forge.aLong(
                min = 0L,
                max = DatadogLateCrashReporter.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD - 1000
            ),
            usr = null
        )
        // The crash follows the last view the crashed run wrote.
        val fakeTimestamp = fakeViewEvent.date + fakeCrashDelayMs
        val fakeServerOffset =
            forge.aLong(min = -60_000L, max = 60_000L)
        fakeDatadogContext = fakeDatadogContext.copy(
            time = fakeDatadogContext.time.copy(
                serverTimeOffsetMs = fakeServerOffset
            )
        )
        val fakeViewEventJson = fakeViewEvent.toJson().asJsonObject
        val fakeEvent = mapOf(
            "timestamp" to fakeTimestamp,
            "timeSinceAppStartMs" to fakeTimeSinceAppStartMs,
            "signalName" to fakeSignalName,
            "stacktrace" to fakeStacktrace,
            "message" to crashMessage,
            "lastViewEvent" to fakeViewEventJson
        )

        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson))
            .doReturn(fakeViewEvent)

        // When
        testedHandler.handleNdkCrashEvent(fakeEvent, mockRumWriter)

        // Then
        argumentCaptor<Any> {
            verify(mockRumWriter, times(2)).write(eq(mockEventBatchWriter), capture(), eq(EventType.CRASH))

            ErrorEventAssert.assertThat(firstValue as ErrorEvent)
                .hasErrorId()
                .hasApplicationId(fakeViewEvent.application.id)
                .hasSessionId(fakeViewEvent.session.id)
                .hasView(
                    fakeViewEvent.view.id,
                    fakeViewEvent.view.name,
                    fakeViewEvent.view.url
                )
                .hasBuildId(fakeDatadogContext.appBuildId)
                .hasMessage(crashMessage)
                .hasStackTrace(fakeStacktrace)
                .isCrash(true)
                .hasErrorSource(RumErrorSource.SOURCE)
                .hasErrorSourceType(ErrorEvent.SourceType.NDK)
                .hasTimestamp(fakeTimestamp + fakeServerOffset)
                .hasNoUserInfo()
                .hasErrorType(fakeSignalName)
                .hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                .hasTimeSinceAppStart(fakeTimeSinceAppStartMs)
                .hasDeviceInfo(
                    fakeDatadogContext.deviceInfo.deviceName,
                    fakeDatadogContext.deviceInfo.deviceModel,
                    fakeDatadogContext.deviceInfo.deviceBrand,
                    fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                    fakeDatadogContext.deviceInfo.architecture
                )
                .hasOsInfo(
                    fakeDatadogContext.deviceInfo.osName,
                    fakeDatadogContext.deviceInfo.osVersion,
                    fakeDatadogContext.deviceInfo.osMajorVersion
                )

            ViewEventAssert.assertThat(secondValue as ViewEvent)
                .hasVersion(fakeViewEvent.dd.documentVersion + 1)
                .hasCrashCount((fakeViewEvent.view.crash?.count ?: 0) + 1)
                .isActive(false)
        }
    }

    @Test
    fun `M send only RUM error W handleNdkCrashEvent() {view is too old}`(
        @StringForgery crashMessage: String,
        @LongForgery(min = 0, max = 1_000) fakeCrashDelayMs: Long,
        @LongForgery(min = 1) fakeTimeSinceAppStartMs: Long,
        @StringForgery fakeSignalName: String,
        @StringForgery fakeStacktrace: String,
        @Forgery viewEvent: ViewEvent,
        @Forgery fakeUserInfo: UserInfo,
        forge: Forge
    ) {
        // Given
        val fakeViewEvent = viewEvent.copy(
            date = fakeCurrentTimeMs - forge.aLong(
                min = DatadogLateCrashReporter.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD + 1,
                max = DatadogLateCrashReporter.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD * 4
            ),
            usr = ViewEvent.Usr(
                id = fakeUserInfo.id,
                name = fakeUserInfo.name,
                email = fakeUserInfo.email,
                anonymousId = fakeUserInfo.anonymousId,
                additionalProperties = fakeUserInfo.additionalProperties.toMutableMap()
            )
        )
        // The crash follows the last view the crashed run wrote.
        val fakeTimestamp = fakeViewEvent.date + fakeCrashDelayMs
        val fakeServerOffset =
            forge.aLong(min = -60_000L, max = 60_000L)
        fakeDatadogContext = fakeDatadogContext.copy(
            time = fakeDatadogContext.time.copy(
                serverTimeOffsetMs = fakeServerOffset
            )
        )
        val fakeViewEventJson = fakeViewEvent.toJson().asJsonObject
        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson))
            .doReturn(fakeViewEvent)
        val expectedErrorEventSource = with(fakeViewEvent.source) {
            if (this != null) {
                ErrorEvent.ErrorEventSource.fromJson(this.toJson().asString)
            } else {
                null
            }
        }
        val fakeEvent = mapOf(
            "timestamp" to fakeTimestamp,
            "timeSinceAppStartMs" to fakeTimeSinceAppStartMs,
            "signalName" to fakeSignalName,
            "stacktrace" to fakeStacktrace,
            "message" to crashMessage,
            "lastViewEvent" to fakeViewEventJson
        )

        // When
        testedHandler.handleNdkCrashEvent(fakeEvent, mockRumWriter)

        // Then
        argumentCaptor<Any> {
            verify(mockRumWriter, times(1)).write(eq(mockEventBatchWriter), capture(), eq(EventType.CRASH))

            ErrorEventAssert.assertThat(firstValue as ErrorEvent)
                .hasErrorId()
                .hasApplicationId(fakeViewEvent.application.id)
                .hasSessionId(fakeViewEvent.session.id)
                .hasBuildId(fakeDatadogContext.appBuildId)
                .hasView(
                    fakeViewEvent.view.id,
                    fakeViewEvent.view.name,
                    fakeViewEvent.view.url
                )
                .hasMessage(crashMessage)
                .hasStackTrace(fakeStacktrace)
                .isCrash(true)
                .hasErrorSource(RumErrorSource.SOURCE)
                .hasErrorSourceType(ErrorEvent.SourceType.NDK)
                .hasTimestamp(fakeTimestamp + fakeServerOffset)
                .hasUserInfo(
                    UserInfo(
                        fakeViewEvent.usr?.anonymousId,
                        fakeViewEvent.usr?.id,
                        fakeViewEvent.usr?.name,
                        fakeViewEvent.usr?.email,
                        fakeViewEvent.usr?.additionalProperties.orEmpty()
                    )
                )
                .hasErrorType(fakeSignalName)
                .hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                .hasTimeSinceAppStart(fakeTimeSinceAppStartMs)
                .hasSource(expectedErrorEventSource)
                .hasDeviceInfo(
                    fakeDatadogContext.deviceInfo.deviceName,
                    fakeDatadogContext.deviceInfo.deviceModel,
                    fakeDatadogContext.deviceInfo.deviceBrand,
                    fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                    fakeDatadogContext.deviceInfo.architecture
                )
                .hasOsInfo(
                    fakeDatadogContext.deviceInfo.osName,
                    fakeDatadogContext.deviceInfo.osVersion,
                    fakeDatadogContext.deviceInfo.osMajorVersion
                )
        }
    }

    @Test
    fun `M not send RUM event W handleNdkCrashEvent() { RUM feature is not registered }`(
        @StringForgery crashMessage: String,
        @LongForgery(min = 1) fakeTimestamp: Long,
        @LongForgery(min = 1) fakeTimeSinceAppStartMs: Long,
        @StringForgery fakeSignalName: String,
        @StringForgery fakeStacktrace: String,
        @Forgery viewEvent: ViewEvent
    ) {
        // Given
        val fakeViewEventJson = viewEvent.toJson().asJsonObject
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn null
        val fakeEvent = mapOf(
            "timestamp" to fakeTimestamp,
            "timeSinceAppStartMs" to fakeTimeSinceAppStartMs,
            "signalName" to fakeSignalName,
            "stacktrace" to fakeStacktrace,
            "message" to crashMessage,
            "lastViewEvent" to fakeViewEventJson
        )

        // When
        testedHandler.handleNdkCrashEvent(fakeEvent, mockRumWriter)

        // Then
        verifyNoInteractions(mockRumWriter, mockEventBatchWriter)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.INFO,
            InternalLogger.Target.USER,
            DatadogLateCrashReporter.INFO_RUM_FEATURE_NOT_REGISTERED
        )
    }

    @Test
    fun `M draw a session of its own W handleNdkCrashEvent() { view json deserialization fails }`(
        @StringForgery crashMessage: String,
        @LongForgery(min = 1) fakeTimestamp: Long,
        @LongForgery(min = 1) fakeTimeSinceAppStartMs: Long,
        @StringForgery fakeSignalName: String,
        @StringForgery fakeStacktrace: String,
        @Forgery fakeViewEventJson: JsonObject
    ) {
        // Given
        val fakeEvent = mutableMapOf(
            "timestamp" to fakeTimestamp,
            "timeSinceAppStartMs" to fakeTimeSinceAppStartMs,
            "signalName" to fakeSignalName,
            "stacktrace" to fakeStacktrace,
            "message" to crashMessage,
            "lastViewEvent" to fakeViewEventJson
        )
        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson)) doReturn null

        // When
        testedHandler.handleNdkCrashEvent(fakeEvent, mockRumWriter)

        // Then - an unreadable view is no view: the session is drawn, and here not kept
        assertThat(drawCount).isEqualTo(1)
        verifyNoInteractions(mockRumWriter, mockEventBatchWriter)
    }

    @ParameterizedTest
    @EnumSource(ValueMissingType::class)
    fun `M not send RUM event W handleNdkCrashEvent() { corrupted event }`(
        missingType: ValueMissingType,
        @StringForgery crashMessage: String,
        @LongForgery(min = 1) fakeTimestamp: Long,
        @StringForgery fakeSignalName: String,
        @StringForgery fakeStacktrace: String,
        @Forgery viewEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val fakeViewEventJson = viewEvent.toJson().asJsonObject
        val fakeEvent = mutableMapOf(
            "timestamp" to fakeTimestamp,
            "signalName" to fakeSignalName,
            "stacktrace" to fakeStacktrace,
            "message" to crashMessage,
            "lastViewEvent" to fakeViewEventJson
        )

        // The last view is not mandatory, see the tests of a crash with no last view.
        val mandatoryKeys = fakeEvent.keys - "lastViewEvent"
        when (missingType) {
            ValueMissingType.MISSING -> fakeEvent.remove(forge.anElementFrom(mandatoryKeys))
            ValueMissingType.NULL -> fakeEvent[forge.anElementFrom(mandatoryKeys)] = null
            ValueMissingType.WRONG_TYPE -> fakeEvent[forge.anElementFrom(mandatoryKeys)] = Any()
        }

        // When
        testedHandler.handleNdkCrashEvent(fakeEvent, mockRumWriter)

        // Then
        verifyNoInteractions(mockRumWriter, mockEventBatchWriter)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            DatadogLateCrashReporter.NDK_CRASH_EVENT_MISSING_MANDATORY_FIELDS
        )
    }

    //endregion

    // region handleAnrCrash

    @Test
    fun `M send RUM view+error W handleAnrCrash()`(
        @Forgery viewEvent: ViewEvent,
        @Forgery fakeUserInfo: UserInfo,
        forge: Forge
    ) {
        // Given
        val fakeViewEvent = viewEvent.copy(
            date = fakeCurrentTimeMs - forge.aLong(
                min = 0L,
                max = DatadogLateCrashReporter.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD - 1000
            ),
            usr = ViewEvent.Usr(
                id = fakeUserInfo.id,
                name = fakeUserInfo.name,
                email = fakeUserInfo.email,
                anonymousId = fakeUserInfo.anonymousId,
                additionalProperties = fakeUserInfo.additionalProperties.toMutableMap()
            )
        )
        val fakeTimestamp = fakeViewEvent.date + forge.aLong(min = 1L, max = 1000000L)
        val fakeServerOffset =
            forge.aLong(min = -60_000L, max = 60_000L)
        fakeDatadogContext = fakeDatadogContext.copy(
            time = fakeDatadogContext.time.copy(
                serverTimeOffsetMs = fakeServerOffset
            )
        )

        val fakeViewEventJson = fakeViewEvent.toJson().asJsonObject

        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson)) doReturn fakeViewEvent

        val fakeThreadsDump = forge.anrCrashThreadDump()
        whenever(mockAndroidTraceParser.parse(any())) doReturn fakeThreadsDump
        val mockAnrExitInfo = mock<ApplicationExitInfo>().apply {
            whenever(traceInputStream) doReturn mock()
            whenever(timestamp) doReturn fakeTimestamp
        }

        // When
        testedHandler.handleAnrCrash(mockAnrExitInfo, fakeViewEventJson, null, true, mockRumWriter)

        // Then
        verify(mockRumFeatureScope).withWriteContext(eq(setOf(Feature.RUM_FEATURE_NAME)), any())
        argumentCaptor<Any> {
            inOrder(mockSdkCore, mockRumWriter) {
                verify(mockSdkCore).writeLastFatalAnrSent(fakeTimestamp)
                verify(mockRumWriter, times(2)).write(eq(mockEventBatchWriter), capture(), eq(EventType.CRASH))
            }

            ErrorEventAssert.assertThat(firstValue as ErrorEvent)
                .hasErrorId()
                .hasApplicationId(fakeViewEvent.application.id)
                .hasSessionId(fakeViewEvent.session.id)
                .hasView(
                    fakeViewEvent.view.id,
                    fakeViewEvent.view.name,
                    fakeViewEvent.view.url
                )
                .hasBuildId(fakeDatadogContext.appBuildId)
                .hasMessage(ANRDetectorRunnable.ANR_MESSAGE)
                .hasStackTrace(fakeThreadsDump.first { it.name == "main" }.stack)
                .isCrash(true)
                .hasErrorSource(RumErrorSource.SOURCE)
                .hasErrorSourceType(ErrorEvent.SourceType.ANDROID)
                .hasTimestamp(fakeTimestamp + fakeServerOffset)
                .hasUserInfo(
                    UserInfo(
                        fakeViewEvent.usr?.anonymousId,
                        fakeViewEvent.usr?.id,
                        fakeViewEvent.usr?.name,
                        fakeViewEvent.usr?.email,
                        fakeViewEvent.usr?.additionalProperties.orEmpty()
                    )
                )
                .hasErrorType(ANRException::class.java.canonicalName)
                .hasErrorCategory(ErrorEvent.Category.ANR)
                .hasTimeSinceAppStart(null)
                .hasDeviceInfo(
                    fakeDatadogContext.deviceInfo.deviceName,
                    fakeDatadogContext.deviceInfo.deviceModel,
                    fakeDatadogContext.deviceInfo.deviceBrand,
                    fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                    fakeDatadogContext.deviceInfo.architecture
                )
                .hasOsInfo(
                    fakeDatadogContext.deviceInfo.osName,
                    fakeDatadogContext.deviceInfo.osVersion,
                    fakeDatadogContext.deviceInfo.osMajorVersion
                )
                .hasThreads(fakeThreadsDump)

            ViewEventAssert.assertThat(secondValue as ViewEvent)
                .hasVersion(fakeViewEvent.dd.documentVersion + 1)
                .hasCrashCount((fakeViewEvent.view.crash?.count ?: 0) + 1)
                .isActive(false)
        }
    }

    @Test
    fun `M send RUM view+error W handleAnrCrash() { view without user }`(
        @Forgery viewEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val fakeViewEvent = viewEvent.copy(
            date = fakeCurrentTimeMs - forge.aLong(
                min = 0L,
                max = DatadogLateCrashReporter.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD - 1000
            ),
            usr = null
        )
        val fakeTimestamp = fakeViewEvent.date + forge.aLong(min = 1L, max = 1000000L)
        val fakeServerOffset =
            forge.aLong(min = -60_000L, max = 60_000L)
        fakeDatadogContext = fakeDatadogContext.copy(
            time = fakeDatadogContext.time.copy(
                serverTimeOffsetMs = fakeServerOffset
            )
        )

        val fakeViewEventJson = fakeViewEvent.toJson().asJsonObject

        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson)) doReturn fakeViewEvent

        val fakeThreadsDump = forge.anrCrashThreadDump()
        whenever(mockAndroidTraceParser.parse(any())) doReturn fakeThreadsDump
        val mockAnrExitInfo = mock<ApplicationExitInfo>().apply {
            whenever(traceInputStream) doReturn mock()
            whenever(timestamp) doReturn fakeTimestamp
        }

        // When
        testedHandler.handleAnrCrash(mockAnrExitInfo, fakeViewEventJson, null, true, mockRumWriter)

        // Then
        verify(mockRumFeatureScope).withWriteContext(eq(setOf(Feature.RUM_FEATURE_NAME)), any())
        argumentCaptor<Any> {
            inOrder(mockSdkCore, mockRumWriter) {
                verify(mockSdkCore).writeLastFatalAnrSent(fakeTimestamp)
                verify(mockRumWriter, times(2)).write(eq(mockEventBatchWriter), capture(), eq(EventType.CRASH))
            }

            ErrorEventAssert.assertThat(firstValue as ErrorEvent)
                .hasErrorId()
                .hasApplicationId(fakeViewEvent.application.id)
                .hasSessionId(fakeViewEvent.session.id)
                .hasView(
                    fakeViewEvent.view.id,
                    fakeViewEvent.view.name,
                    fakeViewEvent.view.url
                )
                .hasBuildId(fakeDatadogContext.appBuildId)
                .hasMessage(ANRDetectorRunnable.ANR_MESSAGE)
                .hasStackTrace(fakeThreadsDump.first { it.name == "main" }.stack)
                .isCrash(true)
                .hasErrorSource(RumErrorSource.SOURCE)
                .hasErrorSourceType(ErrorEvent.SourceType.ANDROID)
                .hasTimestamp(fakeTimestamp + fakeServerOffset)
                .hasNoUserInfo()
                .hasErrorType(ANRException::class.java.canonicalName)
                .hasErrorCategory(ErrorEvent.Category.ANR)
                .hasTimeSinceAppStart(null)
                .hasDeviceInfo(
                    fakeDatadogContext.deviceInfo.deviceName,
                    fakeDatadogContext.deviceInfo.deviceModel,
                    fakeDatadogContext.deviceInfo.deviceBrand,
                    fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                    fakeDatadogContext.deviceInfo.architecture
                )
                .hasOsInfo(
                    fakeDatadogContext.deviceInfo.osName,
                    fakeDatadogContext.deviceInfo.osVersion,
                    fakeDatadogContext.deviceInfo.osMajorVersion
                )
                .hasThreads(fakeThreadsDump)

            ViewEventAssert.assertThat(secondValue as ViewEvent)
                .hasVersion(fakeViewEvent.dd.documentVersion + 1)
                .hasCrashCount((fakeViewEvent.view.crash?.count ?: 0) + 1)
                .isActive(false)
        }
    }

    @Test
    fun `M send RUM view+error W handleAnrCrash() { view of a session kept on error is old }`(
        @Forgery viewEvent: ViewEvent,
        forge: Forge
    ) {
        // Given - a withheld session uploaded nothing: this view is the only one the intake will get
        val fakeViewEvent = viewEvent.copy(
            date = fakeCurrentTimeMs - forge.aLong(
                min = DatadogLateCrashReporter.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD + 1,
                max = DatadogLateCrashReporter.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD * 2
            ),
            session = viewEvent.session.copy(sampledForError = true)
        )
        val fakeTimestamp = fakeViewEvent.date + forge.aLong(min = 1L, max = 1000000L)
        val fakeViewEventJson = fakeViewEvent.toJson().asJsonObject
        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson)) doReturn fakeViewEvent
        whenever(mockAndroidTraceParser.parse(any())) doReturn forge.anrCrashThreadDump()
        val mockAnrExitInfo = mock<ApplicationExitInfo>().apply {
            whenever(traceInputStream) doReturn mock()
            whenever(timestamp) doReturn fakeTimestamp
        }

        // When
        testedHandler.handleAnrCrash(mockAnrExitInfo, fakeViewEventJson, null, true, mockRumWriter)

        // Then - the ANR and the view, unlike a collected session's view this old
        argumentCaptor<Any> {
            verify(mockRumWriter, times(2)).write(eq(mockEventBatchWriter), capture(), eq(EventType.CRASH))
            assertThat((firstValue as ErrorEvent).session.id).isEqualTo(fakeViewEvent.session.id)
            assertThat((secondValue as ViewEvent).session.sampledForError).isTrue
        }
    }

    @Test
    fun `M send only RUM error W handleAnrCrash() { view is too old }`(
        @Forgery viewEvent: ViewEvent,
        @Forgery fakeUserInfo: UserInfo,
        forge: Forge
    ) {
        // Given
        val fakeViewEvent = viewEvent.copy(
            date = fakeCurrentTimeMs - forge.aLong(
                min = DatadogLateCrashReporter.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD + 1,
                max = DatadogLateCrashReporter.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD * 2
            ),
            usr = ViewEvent.Usr(
                id = fakeUserInfo.id,
                name = fakeUserInfo.name,
                email = fakeUserInfo.email,
                anonymousId = fakeUserInfo.anonymousId,
                additionalProperties = fakeUserInfo.additionalProperties.toMutableMap()
            )
        )
        val fakeTimestamp = fakeViewEvent.date + forge.aLong(min = 1L, max = 1000000L)
        val fakeServerOffset =
            forge.aLong(min = -60_000L, max = 60_000L)
        fakeDatadogContext = fakeDatadogContext.copy(
            time = fakeDatadogContext.time.copy(
                serverTimeOffsetMs = fakeServerOffset
            )
        )

        val fakeViewEventJson = fakeViewEvent.toJson().asJsonObject

        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson)) doReturn fakeViewEvent

        val fakeThreadsDump = forge.anrCrashThreadDump()
        whenever(mockAndroidTraceParser.parse(any())) doReturn fakeThreadsDump
        val mockAnrExitInfo = mock<ApplicationExitInfo>().apply {
            whenever(traceInputStream) doReturn mock()
            whenever(timestamp) doReturn fakeTimestamp
        }

        // When
        testedHandler.handleAnrCrash(mockAnrExitInfo, fakeViewEventJson, null, true, mockRumWriter)

        // Then
        verify(mockRumFeatureScope).withWriteContext(eq(setOf(Feature.RUM_FEATURE_NAME)), any())
        argumentCaptor<Any> {
            inOrder(mockSdkCore, mockRumWriter) {
                verify(mockSdkCore).writeLastFatalAnrSent(fakeTimestamp)
                verify(mockRumWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.CRASH))
            }

            ErrorEventAssert.assertThat(firstValue as ErrorEvent)
                .hasErrorId()
                .hasApplicationId(fakeViewEvent.application.id)
                .hasSessionId(fakeViewEvent.session.id)
                .hasView(
                    fakeViewEvent.view.id,
                    fakeViewEvent.view.name,
                    fakeViewEvent.view.url
                )
                .hasBuildId(fakeDatadogContext.appBuildId)
                .hasMessage(ANRDetectorRunnable.ANR_MESSAGE)
                .hasStackTrace(fakeThreadsDump.first { it.name == "main" }.stack)
                .isCrash(true)
                .hasErrorSource(RumErrorSource.SOURCE)
                .hasErrorSourceType(ErrorEvent.SourceType.ANDROID)
                .hasTimestamp(fakeTimestamp + fakeServerOffset)
                .hasUserInfo(
                    UserInfo(
                        fakeViewEvent.usr?.anonymousId,
                        fakeViewEvent.usr?.id,
                        fakeViewEvent.usr?.name,
                        fakeViewEvent.usr?.email,
                        fakeViewEvent.usr?.additionalProperties.orEmpty()
                    )
                )
                .hasErrorType(ANRException::class.java.canonicalName)
                .hasErrorCategory(ErrorEvent.Category.ANR)
                .hasTimeSinceAppStart(null)
                .hasDeviceInfo(
                    fakeDatadogContext.deviceInfo.deviceName,
                    fakeDatadogContext.deviceInfo.deviceModel,
                    fakeDatadogContext.deviceInfo.deviceBrand,
                    fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                    fakeDatadogContext.deviceInfo.architecture
                )
                .hasOsInfo(
                    fakeDatadogContext.deviceInfo.osName,
                    fakeDatadogContext.deviceInfo.osVersion,
                    fakeDatadogContext.deviceInfo.osMajorVersion
                )
                .hasThreads(fakeThreadsDump)
        }
    }

    @Test
    fun `M log warning and not send anything W handleAnrCrash() { RUM feature not registered }`(
        @Forgery viewEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val fakeViewEvent = viewEvent.copy(
            date = fakeCurrentTimeMs - forge.aLong(
                min = 0L,
                max = DatadogLateCrashReporter.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD - 1000
            )
        )
        val fakeTimestamp = fakeViewEvent.date + forge.aLong(min = 1L, max = 1000000L)
        val fakeServerOffset =
            forge.aLong(min = -60_000L, max = 60_000L)
        fakeDatadogContext = fakeDatadogContext.copy(
            time = fakeDatadogContext.time.copy(
                serverTimeOffsetMs = fakeServerOffset
            )
        )

        val fakeViewEventJson = fakeViewEvent.toJson().asJsonObject

        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson)) doReturn fakeViewEvent

        val mockAnrExitInfo = mock<ApplicationExitInfo>().apply {
            whenever(traceInputStream) doReturn mock()
            whenever(timestamp) doReturn fakeTimestamp
        }

        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn null

        // When
        testedHandler.handleAnrCrash(mockAnrExitInfo, fakeViewEventJson, null, true, mockRumWriter)

        // Then
        verifyNoInteractions(mockRumWriter)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            DatadogLateCrashReporter.INFO_RUM_FEATURE_NOT_REGISTERED
        )
    }

    @Test
    fun `M not send anything W handleAnrCrash() { view deserialization fails, new session not kept }`(
        @LongForgery(min = 1) fakeTimestamp: Long,
        @Forgery viewEvent: ViewEvent
    ) {
        // Given
        val fakeViewEventJson = viewEvent.toJson().asJsonObject

        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson)) doReturn null

        val mockAnrExitInfo = mock<ApplicationExitInfo>().apply {
            whenever(traceInputStream) doReturn mock()
            whenever(timestamp) doReturn fakeTimestamp
        }

        // When
        testedHandler.handleAnrCrash(mockAnrExitInfo, fakeViewEventJson, null, true, mockRumWriter)

        // Then
        assertThat(drawCount).isEqualTo(1)
        verifyNoInteractions(mockRumWriter, mockAndroidTraceParser)
    }

    @Test
    fun `M not send anything W handleAnrCrash() { Crash timestamp is before last RUM view }`(
        @Forgery viewEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val fakeViewEvent = viewEvent.copy(
            date = fakeCurrentTimeMs - forge.aLong(
                min = 0L,
                max = DatadogLateCrashReporter.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD - 1000
            )
        )
        val fakeViewEventJson = fakeViewEvent.toJson().asJsonObject

        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson)) doReturn fakeViewEvent

        val mockAnrExitInfo = mock<ApplicationExitInfo>().apply {
            whenever(traceInputStream) doReturn mock()
            whenever(timestamp) doReturn fakeViewEvent.date - 1
        }

        // When
        testedHandler.handleAnrCrash(mockAnrExitInfo, fakeViewEventJson, null, true, mockRumWriter)

        // Then
        verifyNoInteractions(mockRumWriter, mockInternalLogger)
    }

    @Test
    fun `M not send anything W handleAnrCrash() { last view event belongs to the current session }`(
        @Forgery viewEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val fakeViewEvent = viewEvent.copy(
            date = fakeCurrentTimeMs - forge.aLong(
                min = 0L,
                max = DatadogLateCrashReporter.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD - 1000
            )
        )
        val fakeTimestamp = fakeViewEvent.date + forge.aLong(min = 1L, max = 1000000L)
        val fakeServerOffset =
            forge.aLong(min = -60_000L, max = 60_000L)
        fakeDatadogContext = fakeDatadogContext.copy(
            time = fakeDatadogContext.time.copy(
                serverTimeOffsetMs = fakeServerOffset
            ),
            featuresContext = mapOf(
                Feature.RUM_FEATURE_NAME to mapOf(RumContext.SESSION_ID to viewEvent.session.id)
            )
        )

        val fakeViewEventJson = fakeViewEvent.toJson().asJsonObject

        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson)) doReturn fakeViewEvent

        val mockAnrExitInfo = mock<ApplicationExitInfo>().apply {
            whenever(traceInputStream) doReturn mock()
            whenever(timestamp) doReturn fakeTimestamp
        }

        // When
        testedHandler.handleAnrCrash(mockAnrExitInfo, fakeViewEventJson, null, true, mockRumWriter)

        // Then
        verifyNoInteractions(mockRumWriter)
    }

    @Test
    fun `M not send anything W handleAnrCrash() { ANR was already sent }`(
        @Forgery viewEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val fakeViewEvent = viewEvent.copy(
            date = fakeCurrentTimeMs - forge.aLong(
                min = 0L,
                max = DatadogLateCrashReporter.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD - 1000
            )
        )
        val fakeTimestamp = fakeViewEvent.date + forge.aLong(min = 1L, max = 1000000L)
        val fakeServerOffset =
            forge.aLong(min = -60_000L, max = 60_000L)
        fakeDatadogContext = fakeDatadogContext.copy(
            time = fakeDatadogContext.time.copy(
                serverTimeOffsetMs = fakeServerOffset
            )
        )

        val fakeViewEventJson = fakeViewEvent.toJson().asJsonObject

        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson)) doReturn fakeViewEvent

        val mockAnrExitInfo = mock<ApplicationExitInfo>().apply {
            whenever(traceInputStream) doReturn mock()
            whenever(timestamp) doReturn fakeTimestamp
        }
        whenever(mockSdkCore.lastFatalAnrSent) doReturn fakeTimestamp

        // When
        testedHandler.handleAnrCrash(mockAnrExitInfo, fakeViewEventJson, null, true, mockRumWriter)

        // Then
        verifyNoInteractions(mockRumWriter)
    }

    @Test
    fun `M not send anything W handleAnrCrash() { empty threads dump }`(
        @Forgery viewEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val fakeViewEvent = viewEvent.copy(
            date = fakeCurrentTimeMs - forge.aLong(
                min = 0L,
                max = DatadogLateCrashReporter.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD - 1000
            )
        )
        val fakeTimestamp = fakeViewEvent.date + forge.aLong(min = 1L, max = 1000000L)
        val fakeServerOffset =
            forge.aLong(min = -60_000L, max = 60_000L)
        fakeDatadogContext = fakeDatadogContext.copy(
            time = fakeDatadogContext.time.copy(
                serverTimeOffsetMs = fakeServerOffset
            )
        )

        val fakeViewEventJson = fakeViewEvent.toJson().asJsonObject

        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson)) doReturn fakeViewEvent

        val mockAnrExitInfo = mock<ApplicationExitInfo>().apply {
            whenever(traceInputStream) doReturn mock()
            whenever(timestamp) doReturn fakeTimestamp
        }
        whenever(mockAndroidTraceParser.parse(any())) doReturn emptyList()

        // When
        testedHandler.handleAnrCrash(mockAnrExitInfo, fakeViewEventJson, null, true, mockRumWriter)

        // Then
        verifyNoInteractions(mockRumWriter)
    }

    @Test
    fun `M not send anything W handleAnrCrash() { cannot open trace information, IOException }`(
        @LongForgery(min = 1) fakeTimestamp: Long,
        @Forgery viewEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val fakeServerOffset =
            forge.aLong(min = -60_000L, max = 60_000L)
        fakeDatadogContext = fakeDatadogContext.copy(
            time = fakeDatadogContext.time.copy(
                serverTimeOffsetMs = fakeServerOffset
            )
        )

        val fakeViewEvent = viewEvent.copy(
            date = System.currentTimeMillis() - forge.aLong(
                min = 0L,
                max = DatadogLateCrashReporter.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD - 1000
            )
        )
        val fakeViewEventJson = fakeViewEvent.toJson().asJsonObject

        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson)) doReturn fakeViewEvent

        val fakeException = IOException()
        val mockAnrExitInfo = mock<ApplicationExitInfo>().apply {
            whenever(traceInputStream) doThrow fakeException
            // Right after the last view: the ANR belongs to it.
            whenever(timestamp) doReturn fakeViewEvent.date + 1
        }

        // When
        testedHandler.handleAnrCrash(mockAnrExitInfo, fakeViewEventJson, null, true, mockRumWriter)

        // Then
        verifyNoInteractions(mockRumWriter)

        argumentCaptor<() -> String> {
            verify(mockInternalLogger).log(
                level = eq(InternalLogger.Level.ERROR),
                target = eq(InternalLogger.Target.USER),
                messageBuilder = capture(),
                throwable = eq(fakeException),
                onlyOnce = eq(false),
                additionalProperties = isNull()
            )
            assertThat(firstValue()).isEqualTo(DatadogLateCrashReporter.OPEN_ANR_TRACE_ERROR)
        }
    }

    @Test
    fun `M not send anything W handleAnrCrash() { cannot open trace information, null }`(
        @LongForgery(min = 1) fakeTimestamp: Long,
        @Forgery viewEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val fakeServerOffset =
            forge.aLong(min = -60_000L, max = 60_000L)
        fakeDatadogContext = fakeDatadogContext.copy(
            time = fakeDatadogContext.time.copy(
                serverTimeOffsetMs = fakeServerOffset
            )
        )

        val fakeViewEvent = viewEvent.copy(
            date = System.currentTimeMillis() - forge.aLong(
                min = 0L,
                max = DatadogLateCrashReporter.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD - 1000
            )
        )
        val fakeViewEventJson = fakeViewEvent.toJson().asJsonObject

        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson)) doReturn fakeViewEvent

        val mockAnrExitInfo = mock<ApplicationExitInfo>().apply {
            whenever(traceInputStream) doReturn null
            // Right after the last view: the ANR belongs to it.
            whenever(timestamp) doReturn fakeViewEvent.date + 1
        }

        // When
        testedHandler.handleAnrCrash(mockAnrExitInfo, fakeViewEventJson, null, true, mockRumWriter)

        // Then
        verifyNoInteractions(mockRumWriter)

        argumentCaptor<() -> String> {
            verify(mockInternalLogger).log(
                level = eq(InternalLogger.Level.WARN),
                target = eq(InternalLogger.Target.USER),
                messageBuilder = capture(),
                throwable = eq(null),
                onlyOnce = eq(false),
                additionalProperties = isNull()
            )
            assertThat(firstValue()).isEqualTo(DatadogLateCrashReporter.MISSING_ANR_TRACE)
        }
    }

    // endregion

    // region crash with no usable last view

    @Test
    fun `M report in a sampled session of its own W handleNdkCrashEvent() { no last view }`(
        @LongForgery(min = 1, max = 1_000_000_000_000L) fakeTimestamp: Long,
        @StringForgery fakeApplicationId: String,
        @FloatForgery(min = 0f, max = 100f) fakeSampleRate: Float
    ) {
        // Given
        fakeNewSession = DatadogLateCrashReporter.NewSession(fakeApplicationId, fakeSampleRate, sampledForError = false)
        val offset = fakeDatadogContext.time.serverTimeOffsetMs

        // When
        testedHandler.handleNdkCrashEvent(ndkCrash(fakeTimestamp, lastViewEvent = null), mockRumWriter)

        // Then
        argumentCaptor<Any> {
            verify(mockRumWriter, times(2)).write(eq(mockEventBatchWriter), capture(), eq(EventType.CRASH))
            val error = firstValue as ErrorEvent
            val view = secondValue as ViewEvent
            assertThat(view.view.name).isEqualTo("ApplicationLaunch")
            assertThat(view.view.url).isEqualTo("com/datadog/application-launch/view")
            assertThat(view.view.crash?.count).isEqualTo(1)
            assertThat(view.view.isActive).isFalse
            assertThat(view.date).isEqualTo(fakeTimestamp + offset)
            assertThat(view.application.id).isEqualTo(fakeApplicationId)
            assertThat(view.session.sampledForError).isNull()
            assertThat(view.dd.configuration?.sessionSampleRate).isEqualTo(fakeSampleRate)
            assertThat(error.session.id).isEqualTo(view.session.id)
            assertThat(error.view.id).isEqualTo(view.view.id)
            assertThat(error.date).isEqualTo(fakeTimestamp + offset)
            assertThat(error.error.isCrash).isTrue
            assertThat(error.dd.configuration?.sessionSampleRate).isEqualTo(fakeSampleRate)
        }
    }

    @Test
    fun `M report in a session kept on error W handleNdkCrashEvent() { no last view, missed by the rate }`(
        @LongForgery(min = 1, max = 1_000_000_000_000L) fakeTimestamp: Long,
        @StringForgery fakeApplicationId: String
    ) {
        // Given
        fakeNewSession = DatadogLateCrashReporter.NewSession(fakeApplicationId, 0f, sampledForError = true)

        // When
        testedHandler.handleNdkCrashEvent(ndkCrash(fakeTimestamp, lastViewEvent = null), mockRumWriter)

        // Then
        argumentCaptor<Any> {
            verify(mockRumWriter, times(2)).write(eq(mockEventBatchWriter), capture(), eq(EventType.CRASH))
            val view = allValues.filterIsInstance<ViewEvent>().single()
            val error = allValues.filterIsInstance<ErrorEvent>().single()
            assertThat(view.session.sampledForError).isTrue
            assertThat(view.dd.configuration?.sessionSampleRate).isEqualTo(0f)
            assertThat(error.session.id).isEqualTo(view.session.id)
            assertThat(error.dd.configuration?.sessionSampleRate).isEqualTo(0f)
        }
    }

    @Test
    fun `M not report W handleNdkCrashEvent() { no last view, the draw keeps no session }`(
        @LongForgery(min = 1, max = 1_000_000_000_000L) fakeTimestamp: Long
    ) {
        // When
        testedHandler.handleNdkCrashEvent(ndkCrash(fakeTimestamp, lastViewEvent = null), mockRumWriter)

        // Then
        assertThat(drawCount).isEqualTo(1)
        verifyNoInteractions(mockRumWriter)
    }

    @Test
    fun `M not report W handleNdkCrashEvent() { crashed run's session drawn as not tracked }`(
        @LongForgery(min = 10_000, max = 1_000_000_000_000L) fakeTimestamp: Long,
        @StringForgery fakeApplicationId: String
    ) {
        // Given
        fakeNewSession = DatadogLateCrashReporter.NewSession(fakeApplicationId, 100f, sampledForError = false)
        val marker = untrackedMarker(date = fakeTimestamp - 1_000)

        // When
        testedHandler.handleNdkCrashEvent(
            ndkCrash(fakeTimestamp, lastViewEvent = marker, timeSinceAppStartMs = 5_000L),
            mockRumWriter
        )

        // Then
        assertThat(drawCount).isZero()
        verifyNoInteractions(mockRumWriter)
    }

    @Test
    fun `M report in a session of its own W handleNdkCrashEvent() { untracked marker of an earlier run }`(
        @LongForgery(min = 10_000, max = 1_000_000_000_000L) fakeTimestamp: Long,
        @StringForgery fakeApplicationId: String
    ) {
        // Given - marked before the crashed run started
        fakeNewSession = DatadogLateCrashReporter.NewSession(fakeApplicationId, 100f, sampledForError = false)
        val marker = untrackedMarker(date = fakeTimestamp - 6_000)

        // When
        testedHandler.handleNdkCrashEvent(
            ndkCrash(fakeTimestamp, lastViewEvent = marker, timeSinceAppStartMs = 5_000L),
            mockRumWriter
        )

        // Then
        assertThat(drawCount).isEqualTo(1)
        verify(mockRumWriter, times(2)).write(eq(mockEventBatchWriter), any(), eq(EventType.CRASH))
    }

    @Test
    fun `M report in a session of its own W handleNdkCrashEvent() { last view older than 4h before the crash }`(
        @Forgery viewEvent: ViewEvent,
        @StringForgery fakeApplicationId: String
    ) {
        // Given
        fakeNewSession = DatadogLateCrashReporter.NewSession(fakeApplicationId, 100f, sampledForError = false)
        val fakeViewEvent = viewEvent.copy(
            date = 1_000_000_000L,
            view = viewEvent.view.copy(timeSpent = 0L)
        )
        val fakeViewEventJson = fakeViewEvent.toJson().asJsonObject
        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson)) doReturn fakeViewEvent
        val fakeTimestamp = fakeViewEvent.date + DatadogLateCrashReporter.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD + 120_000L

        // When
        testedHandler.handleNdkCrashEvent(ndkCrash(fakeTimestamp, fakeViewEventJson), mockRumWriter)

        // Then
        assertNewSessionNotOf(fakeViewEvent)
    }

    @Test
    fun `M report in a session of its own W handleNdkCrashEvent() { last view written before the run started }`(
        @Forgery viewEvent: ViewEvent,
        @StringForgery fakeApplicationId: String
    ) {
        // Given
        fakeNewSession = DatadogLateCrashReporter.NewSession(fakeApplicationId, 100f, sampledForError = false)
        val fakeViewEvent = viewEvent.copy(
            date = 1_000_000_000L,
            view = viewEvent.view.copy(timeSpent = TimeUnit.SECONDS.toNanos(1))
        )
        val fakeViewEventJson = fakeViewEvent.toJson().asJsonObject
        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson)) doReturn fakeViewEvent
        // The run started 2s after the view's last update and crashed a minute later.
        val fakeTimestamp = fakeViewEvent.date + 3_000L + 60_000L

        // When
        testedHandler.handleNdkCrashEvent(
            ndkCrash(fakeTimestamp, fakeViewEventJson, timeSinceAppStartMs = 60_000L),
            mockRumWriter
        )

        // Then
        assertNewSessionNotOf(fakeViewEvent)
    }

    @Test
    fun `M keep the last view W handleNdkCrashEvent() { last view of the crashed run }`(
        @Forgery viewEvent: ViewEvent
    ) {
        // Given
        val fakeViewEvent = viewEvent.copy(
            date = 1_000_000_000L,
            view = viewEvent.view.copy(timeSpent = TimeUnit.SECONDS.toNanos(1))
        )
        val fakeViewEventJson = fakeViewEvent.toJson().asJsonObject
        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson)) doReturn fakeViewEvent
        val fakeTimestamp = fakeViewEvent.date + 3_000L

        // When
        testedHandler.handleNdkCrashEvent(
            ndkCrash(fakeTimestamp, fakeViewEventJson, timeSinceAppStartMs = 60_000L),
            mockRumWriter
        )

        // Then
        assertThat(drawCount).isZero()
        argumentCaptor<Any> {
            verify(mockRumWriter, atLeastOnce()).write(eq(mockEventBatchWriter), capture(), eq(EventType.CRASH))
            assertThat((firstValue as ErrorEvent).session.id).isEqualTo(fakeViewEvent.session.id)
        }
    }

    @Test
    fun `M report in a session of its own W handleAnrCrash() { no last view, foreground }`(
        @LongForgery(min = 1, max = 1_000_000_000_000L) fakeTimestamp: Long,
        @StringForgery fakeApplicationId: String,
        forge: Forge
    ) {
        // Given
        fakeNewSession = DatadogLateCrashReporter.NewSession(fakeApplicationId, 100f, sampledForError = false)
        whenever(mockAndroidTraceParser.parse(any())) doReturn forge.anrCrashThreadDump()
        val mockAnrExitInfo = anrExitInfo(fakeTimestamp, ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND)

        // When
        testedHandler.handleAnrCrash(mockAnrExitInfo, null, null, true, mockRumWriter)

        // Then
        argumentCaptor<Any> {
            inOrder(mockSdkCore, mockRumWriter) {
                verify(mockSdkCore).writeLastFatalAnrSent(fakeTimestamp)
                verify(mockRumWriter, times(2)).write(eq(mockEventBatchWriter), capture(), eq(EventType.CRASH))
            }
            val error = firstValue as ErrorEvent
            val view = secondValue as ViewEvent
            assertThat(error.error.category).isEqualTo(ErrorEvent.Category.ANR)
            assertThat(error.session.id).isEqualTo(view.session.id)
            assertThat(view.view.name).isEqualTo("ApplicationLaunch")
            assertThat(view.date).isEqualTo(fakeTimestamp + fakeDatadogContext.time.serverTimeOffsetMs)
        }
    }

    @Test
    fun `M report in a Background view W handleAnrCrash() { no last view, background }`(
        @LongForgery(min = 1, max = 1_000_000_000_000L) fakeTimestamp: Long,
        @StringForgery fakeApplicationId: String,
        forge: Forge
    ) {
        // Given
        fakeNewSession = DatadogLateCrashReporter.NewSession(fakeApplicationId, 100f, sampledForError = false)
        whenever(mockAndroidTraceParser.parse(any())) doReturn forge.anrCrashThreadDump()
        val mockAnrExitInfo = anrExitInfo(fakeTimestamp, ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED)

        // When
        testedHandler.handleAnrCrash(mockAnrExitInfo, null, null, true, mockRumWriter)

        // Then
        argumentCaptor<Any> {
            verify(mockRumWriter, times(2)).write(eq(mockEventBatchWriter), capture(), eq(EventType.CRASH))
            val view = secondValue as ViewEvent
            assertThat(view.view.name).isEqualTo("Background")
            assertThat(view.view.url).isEqualTo("com/datadog/background/view")
        }
    }

    @Test
    fun `M not report W handleAnrCrash() { no last view, ANR did not end the last run }`(
        @LongForgery(min = 1, max = 1_000_000_000_000L) fakeTimestamp: Long,
        @StringForgery fakeApplicationId: String
    ) {
        // Given
        fakeNewSession = DatadogLateCrashReporter.NewSession(fakeApplicationId, 100f, sampledForError = false)
        val mockAnrExitInfo = anrExitInfo(fakeTimestamp, ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND)

        // When
        testedHandler.handleAnrCrash(mockAnrExitInfo, null, null, false, mockRumWriter)

        // Then
        assertThat(drawCount).isZero()
        verifyNoInteractions(mockRumWriter, mockAndroidTraceParser)
    }

    @Test
    fun `M not report W handleAnrCrash() { crashed run's session drawn as not tracked }`(
        @LongForgery(min = 10_000, max = 1_000_000_000_000L) fakeTimestamp: Long,
        @StringForgery fakeApplicationId: String
    ) {
        // Given
        fakeNewSession = DatadogLateCrashReporter.NewSession(fakeApplicationId, 100f, sampledForError = false)
        val mockAnrExitInfo = anrExitInfo(fakeTimestamp, ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND)

        // When
        testedHandler.handleAnrCrash(
            mockAnrExitInfo,
            untrackedMarker(date = fakeTimestamp - 1_000),
            fakeTimestamp - 5_000,
            true,
            mockRumWriter
        )

        // Then
        assertThat(drawCount).isZero()
        verifyNoInteractions(mockRumWriter, mockAndroidTraceParser)
    }

    @Test
    fun `M report in a session of its own W handleAnrCrash() { last view of a run before the previous exit }`(
        @Forgery viewEvent: ViewEvent,
        @StringForgery fakeApplicationId: String,
        forge: Forge
    ) {
        // Given - the last view was never consumed: a run in between ended without writing one
        fakeNewSession = DatadogLateCrashReporter.NewSession(fakeApplicationId, 100f, sampledForError = false)
        val fakeViewEvent = viewEvent.copy(
            date = 1_000_000_000L,
            view = viewEvent.view.copy(timeSpent = TimeUnit.SECONDS.toNanos(1))
        )
        val fakeViewEventJson = fakeViewEvent.toJson().asJsonObject
        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson)) doReturn fakeViewEvent
        whenever(mockAndroidTraceParser.parse(any())) doReturn forge.anrCrashThreadDump()
        val fakeTimestamp = fakeViewEvent.date + 60_000L
        val mockAnrExitInfo = anrExitInfo(fakeTimestamp, ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND)

        // When
        testedHandler.handleAnrCrash(
            mockAnrExitInfo,
            fakeViewEventJson,
            fakeViewEvent.date + 10_000L,
            true,
            mockRumWriter
        )

        // Then
        assertNewSessionNotOf(fakeViewEvent)
    }

    @Test
    fun `M keep the last view W handleNdkCrashEvent() { device clock far from the server's }`(
        @Forgery viewEvent: ViewEvent
    ) {
        // Given - the device clock is two hours ahead: the view's server date is two hours behind
        // it, before the run started on the device clock
        val offset = -TimeUnit.HOURS.toMillis(2)
        fakeDatadogContext = fakeDatadogContext.copy(
            time = fakeDatadogContext.time.copy(serverTimeOffsetMs = offset)
        )
        val runStartedAt = 1_000_000_000L
        val fakeViewEvent = viewEvent.copy(
            date = runStartedAt + 500L + offset,
            view = viewEvent.view.copy(timeSpent = 0L)
        )
        val fakeViewEventJson = fakeViewEvent.toJson().asJsonObject
        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson)) doReturn fakeViewEvent
        val fakeTimestamp = runStartedAt + 1_000L

        // When
        testedHandler.handleNdkCrashEvent(
            ndkCrash(fakeTimestamp, fakeViewEventJson, timeSinceAppStartMs = 1_000L),
            mockRumWriter
        )

        // Then
        assertThat(drawCount).isZero()
        argumentCaptor<Any> {
            verify(mockRumWriter, atLeastOnce()).write(eq(mockEventBatchWriter), capture(), eq(EventType.CRASH))
            assertThat((firstValue as ErrorEvent).session.id).isEqualTo(fakeViewEvent.session.id)
        }
    }

    private fun ndkCrash(
        timestamp: Long,
        lastViewEvent: JsonObject?,
        timeSinceAppStartMs: Long? = null
    ): Map<String, Any?> = mapOf(
        "timestamp" to timestamp,
        "timeSinceAppStartMs" to timeSinceAppStartMs,
        "signalName" to "SIGSEGV",
        "stacktrace" to "stack",
        "message" to "NDK crash detected with signal: SIGSEGV",
        "lastViewEvent" to lastViewEvent
    )

    private fun untrackedMarker(date: Long): JsonObject = JsonParser.parseString(
        String(DatadogLateCrashReporter.untrackedSessionMarker("untracked-session", date), Charsets.UTF_8)
    ).asJsonObject

    private fun anrExitInfo(timestamp: Long, importance: Int): ApplicationExitInfo =
        mock<ApplicationExitInfo>().apply {
            whenever(traceInputStream) doReturn mock()
            whenever(this.timestamp) doReturn timestamp
            whenever(this.importance) doReturn importance
        }

    private fun assertNewSessionNotOf(lastViewEvent: ViewEvent) {
        assertThat(drawCount).isEqualTo(1)
        argumentCaptor<Any> {
            verify(mockRumWriter, times(2)).write(eq(mockEventBatchWriter), capture(), eq(EventType.CRASH))
            val error = allValues.filterIsInstance<ErrorEvent>().single()
            val view = allValues.filterIsInstance<ViewEvent>().single()
            assertThat(error.session.id).isEqualTo(view.session.id).isNotEqualTo(lastViewEvent.session.id)
            assertThat(view.view.name).isEqualTo("ApplicationLaunch")
        }
    }

    // endregion

    private fun Forge.anrCrashThreadDump(): List<ThreadDump> {
        val otherThreads = aList { getForgery<ThreadDump>() }.map { it.copy(crashed = false) }
        val mainThread = getForgery<ThreadDump>().copy(name = "main", crashed = true)
        return shuffle(otherThreads + mainThread)
    }

    enum class ValueMissingType {
        MISSING,
        NULL,
        WRONG_TYPE
    }
}
