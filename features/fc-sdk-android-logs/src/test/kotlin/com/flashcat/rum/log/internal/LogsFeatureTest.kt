/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.log.internal

import android.content.Context
import com.flashcat.rum.api.InternalLogger
import com.flashcat.rum.api.context.FlashcatContext
import com.flashcat.rum.api.feature.EventWriteScope
import com.flashcat.rum.api.feature.Feature
import com.flashcat.rum.api.feature.FeatureScope
import com.flashcat.rum.api.feature.FeatureSdkCore
import com.flashcat.rum.api.storage.DataWriter
import com.flashcat.rum.api.storage.EventBatchWriter
import com.flashcat.rum.api.storage.EventType
import com.flashcat.rum.api.storage.FeatureStorageConfiguration
import com.flashcat.rum.event.EventMapper
import com.flashcat.rum.event.MapperSerializer
import com.flashcat.rum.internal.utils.NULL_MAP_VALUE
import com.flashcat.rum.log.LogAttributes
import com.flashcat.rum.log.internal.domain.event.LogEventMapperWrapper
import com.flashcat.rum.log.internal.net.LogsRequestFactory
import com.flashcat.rum.log.internal.storage.LogsDataWriter
import com.flashcat.rum.log.model.LogEvent
import com.flashcat.rum.utils.extension.toIsoFormattedTimestamp
import com.flashcat.rum.utils.forge.Configurator
import com.datadog.tools.unit.forge.exhaustiveAttributes
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
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
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Locale
import java.util.UUID
import com.flashcat.rum.log.assertj.LogEventAssert.Companion.assertThat as assertThatLog

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class LogsFeatureTest {

    private lateinit var testedFeature: LogsFeature

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockLogsFeatureScope: FeatureScope

    @Mock
    lateinit var mockEventWriteScope: EventWriteScope

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    @Mock
    lateinit var mockDataWriter: DataWriter<LogEvent>

    @Mock
    lateinit var mockEventMapper: EventMapper<LogEvent>

    @Mock
    lateinit var mockApplicationContext: Context

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Forgery
    lateinit var fakeFlashcatContext: FlashcatContext

    @Forgery
    lateinit var fakeRumApplicationId: UUID

    @Forgery
    lateinit var fakeRumSessionId: UUID

    @Forgery
    lateinit var fakeRumViewId: UUID

    @Forgery
    lateinit var fakeRumActionId: UUID

    @StringForgery(regex = "https://[a-z]+\\.com")
    lateinit var fakeEndpointUrl: String

    @StringForgery(StringForgeryType.HEXADECIMAL)
    lateinit var fakeSpanId: String

    @StringForgery(StringForgeryType.HEXADECIMAL)
    lateinit var fakeTraceId: String

    @StringForgery(StringForgeryType.ALPHABETICAL)
    lateinit var fakeThreadName: String

    @StringForgery(regex = "[a-z]{2,4}(\\.[a-z]{3,8}){2,4}")
    lateinit var fakePackageName: String

    private var fakeServerTimeOffset: Long = 0L

    @BeforeEach
    fun `set up`(
        forge: Forge
    ) {
        val now = System.currentTimeMillis()
        fakeServerTimeOffset = forge.aLong(min = -now, max = Long.MAX_VALUE - now)

        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger

        whenever(mockApplicationContext.packageName) doReturn fakePackageName
        whenever(
            mockSdkCore.getFeature(Feature.LOGS_FEATURE_NAME)
        ) doReturn mockLogsFeatureScope

        whenever(mockEventWriteScope.invoke(any())) doAnswer {
            val callback = it.getArgument<(EventBatchWriter) -> Unit>(0)
            callback.invoke(mockEventBatchWriter)
        }
        whenever(mockLogsFeatureScope.withWriteContext(any(), any())) doAnswer {
            val callback = it.getArgument<(FlashcatContext, EventWriteScope) -> Unit>(it.arguments.lastIndex)
            callback.invoke(fakeFlashcatContext, mockEventWriteScope)
        }

        fakeFlashcatContext = fakeFlashcatContext.copy(
            time = fakeFlashcatContext.time.copy(
                serverTimeOffsetMs = fakeServerTimeOffset
            ),
            featuresContext = fakeFlashcatContext.featuresContext.toMutableMap().apply {
                put(
                    Feature.RUM_FEATURE_NAME,
                    mapOf(
                        "application_id" to fakeRumApplicationId,
                        "session_id" to fakeRumSessionId,
                        "view_id" to fakeRumViewId,
                        "action_id" to fakeRumActionId
                    )
                )
                put(
                    Feature.TRACING_FEATURE_NAME,
                    mapOf(
                        "context@$fakeThreadName" to mapOf(
                            "span_id" to fakeSpanId,
                            "trace_id" to fakeTraceId
                        )
                    )
                )
            }
        )

        testedFeature = LogsFeature(mockSdkCore, fakeEndpointUrl, mockEventMapper)
    }

    @Test
    fun `M initialize data writer W initialize()`() {
        // When
        testedFeature.onInitialize(mockApplicationContext)

        // Then
        assertThat(testedFeature.dataWriter)
            .isInstanceOf(LogsDataWriter::class.java)
    }

    @Test
    fun `M use the eventMapper W initialize()`() {
        // When
        testedFeature.onInitialize(mockApplicationContext)

        // Then
        val dataWriter = testedFeature.dataWriter as? LogsDataWriter
        val logMapperSerializer = dataWriter?.serializer as? MapperSerializer<LogEvent>
        val logEventMapperWrapper = logMapperSerializer
            ?.getFieldValue<LogEventMapperWrapper, MapperSerializer<LogEvent>>("eventMapper")
        val logEventMapper = logEventMapperWrapper?.wrappedEventMapper
        assertThat(logEventMapper).isSameAs(mockEventMapper)
    }

    @Test
    fun `M initialize packageName W initialize()`() {
        // When
        testedFeature.onInitialize(mockApplicationContext)

        // Then
        assertThat(testedFeature.packageName).isEqualTo(fakePackageName)
    }

    @Test
    fun `M provide logs feature name W name()`() {
        // When+Then
        assertThat(testedFeature.name)
            .isEqualTo(Feature.LOGS_FEATURE_NAME)
    }

    @Test
    fun `M provide logs request factory W requestFactory()`() {
        // When+Then
        assertThat(testedFeature.requestFactory)
            .isInstanceOf(LogsRequestFactory::class.java)
    }

    @Test
    fun `M provide default storage configuration W storageConfiguration()`() {
        // When+Then
        assertThat(testedFeature.storageConfiguration)
            .isEqualTo(FeatureStorageConfiguration.DEFAULT)
    }

    @Test
    fun `M add attributes W addAttribute`(
        @StringForgery key: String,
        @StringForgery value: String
    ) {
        // When
        testedFeature.addAttribute(key, value)

        // Then
        val attributes = testedFeature.getAttributes()
        assertThat(attributes).containsEntry(key, value)
    }

    @Test
    fun `M remove attributes W removeAttribute`(
        @StringForgery key: String,
        @StringForgery value: String
    ) {
        // Given
        testedFeature.addAttribute(key, value)

        // When
        testedFeature.removeAttribute(key)

        // Then
        val attributes = testedFeature.getAttributes()
        assertThat(attributes).isEmpty()
    }

    @Test
    fun `M provide attribute snapshot W getAttributes`(
        @StringForgery key: String,
        @StringForgery value: String,
        @StringForgery secondValue: String
    ) {
        // Given
        testedFeature.addAttribute(key, value)
        val attributes = testedFeature.getAttributes()

        // When
        testedFeature.addAttribute(key, secondValue)

        // Then
        assertThat(attributes).containsEntry(key, value)
    }

    @Test
    fun `M add attributes replaces null W addAttribute { null value }`(
        @StringForgery key: String
    ) {
        testedFeature.addAttribute(key, null)

        // Then
        assertThat(testedFeature.getAttributes()).containsEntry(key, NULL_MAP_VALUE)
    }

    // region FeatureEventReceiver#onReceive + unknown

    @Test
    fun `M log warning and do nothing W onReceive() { unknown event type }`() {
        // Given
        testedFeature.dataWriter = mockDataWriter

        // When
        testedFeature.onReceive(Any())

        // Then
        argumentCaptor<() -> String> {
            verify(mockInternalLogger).log(
                eq(InternalLogger.Level.WARN),
                eq(InternalLogger.Target.USER),
                capture(),
                isNull(),
                eq(false),
                eq(null)
            )
            assertThat(firstValue()).isEqualTo(
                LogsFeature.UNSUPPORTED_EVENT_TYPE.format(
                    Locale.US,
                    Any()::class.java.canonicalName
                )
            )
        }
        verifyNoMoreInteractions(mockInternalLogger)
        verifyNoInteractions(mockDataWriter)
    }

    @Test
    fun `M log warning and do nothing W onReceive() { unknown type property value }`(
        forge: Forge
    ) {
        // Given
        testedFeature.dataWriter = mockDataWriter
        val event = mapOf(
            "type" to forge.anAlphabeticalString()
        )

        // When
        testedFeature.onReceive(event)

        // Then
        argumentCaptor<() -> String> {
            verify(mockInternalLogger).log(
                eq(InternalLogger.Level.WARN),
                eq(InternalLogger.Target.USER),
                capture(),
                isNull(),
                eq(false),
                eq(null)
            )
            assertThat(firstValue()).isEqualTo(
                LogsFeature.UNKNOWN_EVENT_TYPE_PROPERTY_VALUE.format(Locale.US, event["type"])
            )
        }

        verifyNoMoreInteractions(mockInternalLogger)
        verifyNoInteractions(mockDataWriter)
    }

    // endregion

    // region FeatureEventReceiver#onReceive + span log event

    @ParameterizedTest
    @EnumSource(ValueMissingType::class)
    fun `M log warning and do nothing W onReceive() { corrupted mandatory fields, span log }`(
        missingType: ValueMissingType,
        @LongForgery fakeTimestamp: Long,
        @StringForgery fakeMessage: String,
        @StringForgery fakeLoggerName: String,
        forge: Forge
    ) {
        // Given
        testedFeature.dataWriter = mockDataWriter
        val fakeAttributes = forge.exhaustiveAttributes()
        val event = mutableMapOf<String, Any?>(
            "type" to "span_log",
            "timestamp" to fakeTimestamp,
            "message" to fakeMessage,
            "loggerName" to fakeLoggerName,
            "attributes" to fakeAttributes
        )

        when (missingType) {
            ValueMissingType.MISSING -> event.remove(
                forge.anElementFrom(event.keys.filterNot { it == "type" })
            )

            ValueMissingType.NULL -> event[
                forge.anElementFrom(event.keys.filterNot { it == "type" })
            ] = null

            ValueMissingType.WRONG_TYPE -> event[
                forge.anElementFrom(event.keys.filterNot { it == "type" })
            ] = Any()
        }

        // When
        testedFeature.onReceive(event)

        // Then
        argumentCaptor<() -> String> {
            verify(mockInternalLogger).log(
                eq(InternalLogger.Level.WARN),
                eq(InternalLogger.Target.USER),
                capture(),
                isNull(),
                eq(false),
                eq(null)
            )
            assertThat(firstValue()).isEqualTo(
                LogsFeature.SPAN_LOG_EVENT_MISSING_MANDATORY_FIELDS_WARNING
            )
        }
        verifyNoMoreInteractions(mockInternalLogger)
        verifyNoInteractions(mockDataWriter)
    }

    @Test
    fun `M write span log event W onReceive() { span log }`(
        @LongForgery fakeTimestamp: Long,
        @StringForgery fakeMessage: String,
        @StringForgery fakeLoggerName: String,
        forge: Forge
    ) {
        // Given
        testedFeature.dataWriter = mockDataWriter
        val fakeAttributes = forge.exhaustiveAttributes()
        val event = mutableMapOf<String, Any?>(
            "type" to "span_log",
            "timestamp" to fakeTimestamp,
            "message" to fakeMessage,
            "loggerName" to fakeLoggerName,
            "attributes" to fakeAttributes
        )

        // When
        testedFeature.onReceive(event)

        // Then
        verify(mockLogsFeatureScope).withWriteContext(eq(setOf(Feature.RUM_FEATURE_NAME)), any())
        argumentCaptor<LogEvent> {
            verify(mockDataWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))

            val log = lastValue

            assertThatLog(log)
                .hasStatus(LogEvent.Status.TRACE)
                .hasLoggerName(fakeLoggerName)
                .hasServiceName(fakeFlashcatContext.service)
                .hasMessage(fakeMessage)
                .hasThreadName(Thread.currentThread().name)
                .hasDate((fakeTimestamp + fakeServerTimeOffset).toIsoFormattedTimestamp())
                .hasNetworkInfo(fakeFlashcatContext.networkInfo)
                .hasUserInfo(fakeFlashcatContext.userInfo)
                .hasBuildId(fakeFlashcatContext.appBuildId)
                .hasExactlyAttributes(
                    fakeAttributes + mapOf(
                        LogAttributes.RUM_APPLICATION_ID to fakeRumApplicationId,
                        LogAttributes.RUM_SESSION_ID to fakeRumSessionId,
                        LogAttributes.RUM_VIEW_ID to fakeRumViewId,
                        LogAttributes.RUM_ACTION_ID to fakeRumActionId
                    )
                )
                .hasExactlyTags(
                    setOf(
                        "${LogAttributes.ENV}:${fakeFlashcatContext.env}",
                        "${LogAttributes.APPLICATION_VERSION}:${fakeFlashcatContext.version}",
                        "${LogAttributes.VARIANT}:${fakeFlashcatContext.variant}",
                        "${LogAttributes.SERVICE}:${fakeFlashcatContext.service}"
                    )
                )
        }
    }

    // endregion

    enum class ValueMissingType {
        MISSING,
        NULL,
        WRONG_TYPE
    }

    inline fun <reified T, R : Any> R?.getFieldValue(
        fieldName: String,
        enclosingClass: Class<R>? = this?.javaClass
    ): T? {
        if (this == null || enclosingClass == null) return null
        val field = enclosingClass.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(this) as T
    }
}
