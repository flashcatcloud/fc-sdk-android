/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.log.internal.domain

import com.flashcat.rum.api.context.AccountInfo
import com.flashcat.rum.api.context.FlashcatContext
import com.flashcat.rum.api.context.NetworkInfo
import com.flashcat.rum.api.context.UserInfo
import com.flashcat.rum.api.feature.Feature
import com.flashcat.rum.core.feature.event.ThreadDump
import com.flashcat.rum.log.LogAttributes
import com.flashcat.rum.log.assertj.LogEventAssert.Companion.assertThat
import com.flashcat.rum.log.model.LogEvent
import com.flashcat.rum.utils.extension.asLogStatus
import com.flashcat.rum.utils.extension.toIsoFormattedTimestamp
import com.flashcat.rum.utils.forge.Configurator
import com.datadog.tools.unit.forge.aThrowable
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.util.UUID

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogLogGeneratorTest {

    lateinit var testedLogGenerator: DatadogLogGenerator

    lateinit var fakeServiceName: String
    lateinit var fakeLoggerName: String
    lateinit var fakeAttributes: Map<String, Any?>
    lateinit var fakeTags: Set<String>
    lateinit var fakeLogMessage: String
    lateinit var fakeThrowable: Throwable

    @StringForgery(StringForgeryType.HEXADECIMAL)
    lateinit var fakeSpanId: String

    @StringForgery(StringForgeryType.HEXADECIMAL)
    lateinit var fakeTraceId: String

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

    var fakeTimestamp = 0L
    var fakeLevel: Int = 0
    lateinit var fakeThreadName: String
    private var fakeTimeOffset: Long = 0L

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeServiceName = forge.anAlphabeticalString()
        fakeLoggerName = forge.anAlphabeticalString()
        fakeLogMessage = forge.anAlphabeticalString()
        fakeLevel = forge.anInt(2, 8)
        fakeAttributes = forge.aMap { anAlphabeticalString() to anInt() }
        fakeTags = forge.aList { anAlphabeticalString() }.toSet()
        fakeThrowable = forge.aThrowable()
        fakeTimestamp = System.currentTimeMillis()
        fakeThreadName = forge.anAlphabeticalString()
        fakeTimeOffset = forge.aLong(
            min = -fakeTimestamp,
            max = Long.MAX_VALUE - fakeTimestamp
        )

        fakeFlashcatContext = fakeFlashcatContext.copy(
            time = fakeFlashcatContext.time.copy(
                serverTimeOffsetMs = fakeTimeOffset
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

        testedLogGenerator = DatadogLogGenerator(
            fakeServiceName
        )
    }

    @Test
    fun `M add log message W creating the Log`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = true,
            fakeLoggerName
        )

        // THEN
        assertThat(log).hasMessage(fakeLogMessage)
    }

    @Test
    fun `M add the log level W creating the Log`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = true,
            fakeLoggerName
        )

        // THEN
        assertThat(log).hasStatus(fakeLevel.asLogStatus())
    }

    @Test
    fun `M add the service name W creating the Log`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = true,
            fakeLoggerName
        )

        // THEN
        assertThat(log).hasServiceName(fakeServiceName)
    }

    @Test
    fun `M add the service name from Datadog context W creating the Log { no service name }`() {
        // WHEN
        testedLogGenerator = DatadogLogGenerator()
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = true,
            fakeLoggerName
        )

        // THEN
        assertThat(log).hasServiceName(fakeFlashcatContext.service)
    }

    @Test
    fun `M add the logger name W creating the Log`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = true,
            fakeLoggerName
        )

        // THEN
        assertThat(log).hasLoggerName(fakeLoggerName)
    }

    @Test
    fun `M add the logger version W creating the Log`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = true,
            fakeLoggerName
        )

        // THEN
        assertThat(log).hasLoggerVersion(fakeFlashcatContext.sdkVersion)
    }

    @Test
    fun `M add the thread name W creating the Log`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = true,
            fakeLoggerName
        )

        // THEN
        assertThat(log).hasThreadName(fakeThreadName)
    }

    @Test
    fun `M add build ID W creating the Log`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = true,
            fakeLoggerName
        )

        // THEN
        assertThat(log).hasBuildId(fakeFlashcatContext.appBuildId)
    }

    @Test
    fun `M add the log timestamp and correct the server offset W creating the Log`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = true,
            fakeLoggerName
        )

        // THEN
        val expected = (fakeTimestamp + fakeTimeOffset)
            .toIsoFormattedTimestamp()
        assertThat(log).hasDate(expected)
    }

    @Test
    fun `M add the throwable W creating the Log`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = true,
            fakeLoggerName
        )

        // THEN
        assertThat(log).hasError(
            LogEvent.Error(
                kind = fakeThrowable.javaClass.canonicalName,
                stack = fakeThrowable.stackTraceToString(),
                message = fakeThrowable.message,
                threads = null
            )
        )
    }

    @Test
    fun `M not add sourceType W creating the Log { source_type attribute not set }`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = true,
            fakeLoggerName
        )

        // THEN
        assertThat(log).hasError(
            LogEvent.Error(
                kind = fakeThrowable.javaClass.canonicalName,
                stack = fakeThrowable.stackTraceToString(),
                message = fakeThrowable.message,
                sourceType = null,
                threads = null
            )
        )
    }

    @Test
    fun `M add sourceType W creating the Log { source_type attribute set }`() {
        // WHEN
        val modifiedAttributes = fakeAttributes.toMutableMap().apply {
            put(LogAttributes.SOURCE_TYPE, "fake_source_type")
        }
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable.javaClass.canonicalName,
            fakeThrowable.message,
            fakeThrowable.stackTraceToString(),
            modifiedAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = true,
            fakeLoggerName
        )

        // THEN
        assertThat(log).hasError(
            LogEvent.Error(
                kind = fakeThrowable.javaClass.canonicalName,
                stack = fakeThrowable.stackTraceToString(),
                message = fakeThrowable.message,
                sourceType = "fake_source_type",
                threads = null
            )
        )
        assertThat(log.additionalProperties).doesNotContainKey(LogAttributes.SOURCE_TYPE)
    }

    @Test
    fun `M not add fingerprint W creating the Log { fingerprint attribute not set }`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = true,
            fakeLoggerName
        )

        // THEN
        assertThat(log).hasError(
            LogEvent.Error(
                kind = fakeThrowable.javaClass.canonicalName,
                stack = fakeThrowable.stackTraceToString(),
                message = fakeThrowable.message,
                sourceType = null,
                fingerprint = null,
                threads = null
            )
        )
    }

    @Test
    fun `M add fingerprint W creating the Log { fingerprint attribute set }`(
        @StringForgery fakeFingerprint: String
    ) {
        // WHEN
        val modifiedAttributes = fakeAttributes.toMutableMap().apply {
            put(LogAttributes.ERROR_FINGERPRINT, fakeFingerprint)
        }
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            modifiedAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = true,
            fakeLoggerName
        )

        // THEN
        assertThat(log).hasError(
            LogEvent.Error(
                kind = fakeThrowable.javaClass.canonicalName,
                stack = fakeThrowable.stackTraceToString(),
                message = fakeThrowable.message,
                sourceType = null,
                fingerprint = fakeFingerprint,
                threads = null
            )
        )
        assertThat(log.additionalProperties).doesNotContainKey(LogAttributes.ERROR_FINGERPRINT)
    }

    @Test
    fun `M add fingerprint W creating the Log { expanded error, fingerprint attribute set }`() {
        // WHEN
        val modifiedAttributes = fakeAttributes.toMutableMap().apply {
            put(LogAttributes.ERROR_FINGERPRINT, "fake_fingerprint")
        }
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable.javaClass.canonicalName,
            fakeThrowable.message,
            fakeThrowable.stackTraceToString(),
            modifiedAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = true,
            fakeLoggerName
        )

        // THEN
        assertThat(log).hasError(
            LogEvent.Error(
                kind = fakeThrowable.javaClass.canonicalName,
                stack = fakeThrowable.stackTraceToString(),
                message = fakeThrowable.message,
                sourceType = null,
                fingerprint = "fake_fingerprint",
                threads = null
            )
        )
        assertThat(log.additionalProperties).doesNotContainKey(LogAttributes.ERROR_FINGERPRINT)
    }

    @Test
    fun `M add the thread dump W creating the Log`(
        @Forgery fakeThreads: List<ThreadDump>
    ) {
        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = true,
            fakeLoggerName,
            threads = fakeThreads
        )

        // THEN
        assertThat(log).hasError(
            LogEvent.Error(
                kind = fakeThrowable.javaClass.canonicalName,
                stack = fakeThrowable.stackTraceToString(),
                message = fakeThrowable.message,
                threads = fakeThreads.map {
                    LogEvent.Thread(
                        name = it.name,
                        crashed = it.crashed,
                        state = it.state,
                        stack = it.stack
                    )
                }.ifEmpty { null }
            )
        )
    }

    @Test
    fun `M use the Throwable class simple name W creating the Log { Throwable anonymous }`() {
        // WHEN
        val fakeAnonymousThrowable = object : Throwable(cause = fakeThrowable) {}
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeAnonymousThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = true,
            fakeLoggerName
        )

        // THEN
        assertThat(log).hasError(
            LogEvent.Error(
                kind = fakeAnonymousThrowable.javaClass.simpleName,
                stack = fakeAnonymousThrowable.stackTraceToString(),
                message = fakeAnonymousThrowable.message,
                threads = null
            )
        )
    }

    @Test
    fun `M use custom userInfo W creating the Log { userInfo provided }`(
        @Forgery fakeCustomUserInfo: UserInfo
    ) {
        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = true,
            fakeLoggerName,
            userInfo = fakeCustomUserInfo
        )

        // THEN
        assertThat(log).hasUserInfo(fakeCustomUserInfo)
    }

    @Test
    fun `M add the userInfo W creating the Log`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = true,
            fakeLoggerName
        )

        // THEN
        assertThat(log).hasUserInfo(fakeFlashcatContext.userInfo)
    }

    @Test
    fun `M use custom accountInfo W creating the Log { accountInfo provided }`(
        @Forgery fakeCustomAccountInfo: AccountInfo
    ) {
        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = true,
            fakeLoggerName,
            accountInfo = fakeCustomAccountInfo
        )

        // THEN
        assertThat(log).hasAccountInfo(fakeCustomAccountInfo)
    }

    @Test
    fun `M add the accountInfo W creating the Log`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = true,
            fakeLoggerName
        )

        // THEN
        assertThat(log).hasAccountInfo(fakeFlashcatContext.accountInfo)
    }

    @Test
    fun `M add the networkInfo W creating the Log`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = true,
            fakeLoggerName
        )

        // THEN
        assertThat(log).hasNetworkInfo(fakeFlashcatContext.networkInfo)
    }

    @Test
    fun `M not add the networkInfo W creating Log {networkInfoProvider is null}`() {
        // GIVEN
        testedLogGenerator = DatadogLogGenerator(
            fakeServiceName
        )
        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = false,
            fakeLoggerName
        )

        // THEN
        assertThat(log).doesNotHaveNetworkInfo()
    }

    @Test
    fun `M use custom networkInfo W creating Log { networkInfo provided }`(
        @Forgery fakeCustomNetworkInfo: NetworkInfo
    ) {
        // GIVEN
        testedLogGenerator = DatadogLogGenerator(
            fakeServiceName
        )
        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = false,
            fakeLoggerName,
            networkInfo = fakeCustomNetworkInfo
        )

        // THEN
        assertThat(log).hasNetworkInfo(fakeCustomNetworkInfo)
    }

    @Test
    fun `M add the envNameTag W not empty`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = true,
            fakeLoggerName
        )

        // THEN
        val deserializedTags = log.ddtags.split(",")
        assertThat(deserializedTags)
            .contains("${LogAttributes.ENV}:${fakeFlashcatContext.env}")
    }

    @Test
    fun `M not add the envNameTag W empty`() {
        // GIVEN
        fakeFlashcatContext = fakeFlashcatContext.copy(
            env = ""
        )

        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = true,
            fakeLoggerName
        )

        // THEN
        val expectedTags = fakeTags +
            "${LogAttributes.APPLICATION_VERSION}:${fakeFlashcatContext.version}" +
            "${LogAttributes.VARIANT}:${fakeFlashcatContext.variant}" +
            "${LogAttributes.SERVICE}:${fakeFlashcatContext.service}"
        assertThat(log).hasExactlyTags(expectedTags)
    }

    @Test
    fun `M add the appVersionTag W not empty`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = true,
            fakeLoggerName
        )

        // THEN
        val deserializedTags = log.ddtags.split(",")
        assertThat(deserializedTags)
            .contains("${LogAttributes.APPLICATION_VERSION}:${fakeFlashcatContext.version}")
    }

    @Test
    fun `M not add the appVersionTag W empty`() {
        // GIVEN
        fakeFlashcatContext = fakeFlashcatContext.copy(
            version = ""
        )

        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = true,
            fakeLoggerName
        )

        // THEN
        val expectedTags = fakeTags +
            "${LogAttributes.ENV}:${fakeFlashcatContext.env}" +
            "${LogAttributes.VARIANT}:${fakeFlashcatContext.variant}" +
            "${LogAttributes.SERVICE}:${fakeFlashcatContext.service}"
        assertThat(log).hasExactlyTags(expectedTags)
    }

    @Test
    fun `M add the variantTag W not empty`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = true,
            fakeLoggerName
        )

        // THEN
        val deserializedTags = log.ddtags.split(",")
        assertThat(deserializedTags)
            .contains("${LogAttributes.VARIANT}:${fakeFlashcatContext.variant}")
    }

    @Test
    fun `M not add the variantTag W empty`() {
        // GIVEN
        fakeFlashcatContext = fakeFlashcatContext.copy(
            variant = ""
        )

        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = true,
            fakeLoggerName
        )

        // THEN
        val expectedTags = fakeTags +
            "${LogAttributes.ENV}:${fakeFlashcatContext.env}" +
            "${LogAttributes.APPLICATION_VERSION}:${fakeFlashcatContext.version}" +
            "${LogAttributes.SERVICE}:${fakeFlashcatContext.service}"
        assertThat(log).hasExactlyTags(expectedTags)
    }

    @Test
    fun `M add the serviceTag W not empty`() {
        // When
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = true,
            fakeLoggerName
        )

        // Then
        val deserializedTags = log.ddtags.split(",")
        assertThat(deserializedTags)
            .contains("${LogAttributes.SERVICE}:${fakeFlashcatContext.service}")
    }

    @Test
    fun `M not add the serviceTag W empty`() {
        // Given
        fakeFlashcatContext = fakeFlashcatContext.copy(
            service = ""
        )

        // When
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = true,
            fakeLoggerName
        )

        // Then
        val expectedTags = fakeTags +
            "${LogAttributes.ENV}:${fakeFlashcatContext.env}" +
            "${LogAttributes.APPLICATION_VERSION}:${fakeFlashcatContext.version}" +
            "${LogAttributes.VARIANT}:${fakeFlashcatContext.variant}"
        assertThat(log).hasExactlyTags(expectedTags)
    }

    @Test
    fun `M add architecture W created a log`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = true,
            fakeLoggerName
        )

        // THEN
        assertThat(log).hasDeviceArchitecture(fakeFlashcatContext.deviceInfo.architecture)
    }

    @Test
    fun `M bundle the trace information W required`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = true,
            fakeLoggerName
        )

        // THEN
        assertThat(log.additionalProperties).containsAllEntriesOf(
            mapOf(
                LogAttributes.DD_TRACE_ID to fakeTraceId,
                LogAttributes.DD_SPAN_ID to fakeSpanId
            )
        )
    }

    @Test
    fun `M do nothing W required to bundle the trace information {no active Span for given thread}`(
        @StringForgery fakeOtherThreadName: String,
        @StringForgery(StringForgeryType.HEXADECIMAL) fakeOtherThreadSpanId: String,
        @StringForgery(StringForgeryType.HEXADECIMAL) fakeOtherThreadTraceId: String
    ) {
        // GIVEN
        fakeFlashcatContext = fakeFlashcatContext.copy(
            featuresContext = fakeFlashcatContext.featuresContext.toMutableMap().apply {
                put(
                    Feature.TRACING_FEATURE_NAME,
                    mapOf(
                        "context@$fakeOtherThreadName" to mapOf(
                            "span_id" to fakeOtherThreadSpanId,
                            "trace_id" to fakeOtherThreadTraceId
                        )
                    )
                )
            }
        )

        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = true,
            fakeLoggerName
        )

        // THEN
        val expectedAttributes = fakeAttributes + mapOf(
            LogAttributes.RUM_APPLICATION_ID to fakeRumApplicationId,
            LogAttributes.RUM_SESSION_ID to fakeRumSessionId,
            LogAttributes.RUM_VIEW_ID to fakeRumViewId,
            LogAttributes.RUM_ACTION_ID to fakeRumActionId
        )
        assertThat(log).hasExactlyAttributes(expectedAttributes)
    }

    @Test
    fun `M do nothing W required to bundle the trace information {no tracing feature context}`() {
        // GIVEN
        fakeFlashcatContext = fakeFlashcatContext.copy(
            featuresContext = fakeFlashcatContext.featuresContext.toMutableMap().apply {
                remove(Feature.TRACING_FEATURE_NAME)
            }
        )

        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = true,
            fakeLoggerName
        )

        // THEN
        val expectedAttributes = fakeAttributes + mapOf(
            LogAttributes.RUM_APPLICATION_ID to fakeRumApplicationId,
            LogAttributes.RUM_SESSION_ID to fakeRumSessionId,
            LogAttributes.RUM_VIEW_ID to fakeRumViewId,
            LogAttributes.RUM_ACTION_ID to fakeRumActionId
        )
        assertThat(log).hasExactlyAttributes(expectedAttributes)
    }

    @Test
    fun `M do nothing W not required to bundle the trace information`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = true,
            fakeLoggerName,
            bundleWithTraces = false
        )

        // THEN
        val expectedAttributes = fakeAttributes + mapOf(
            LogAttributes.RUM_APPLICATION_ID to fakeRumApplicationId,
            LogAttributes.RUM_SESSION_ID to fakeRumSessionId,
            LogAttributes.RUM_VIEW_ID to fakeRumViewId,
            LogAttributes.RUM_ACTION_ID to fakeRumActionId
        )
        assertThat(log).hasExactlyAttributes(expectedAttributes)
    }

    @Test
    fun `M bundle the RUM information W required`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = true,
            fakeLoggerName
        )

        // THEN
        assertThat(log.additionalProperties).containsAllEntriesOf(
            mapOf(
                LogAttributes.RUM_APPLICATION_ID to fakeRumApplicationId,
                LogAttributes.RUM_SESSION_ID to fakeRumSessionId,
                LogAttributes.RUM_VIEW_ID to fakeRumViewId,
                LogAttributes.RUM_ACTION_ID to fakeRumActionId
            )
        )
    }

    @Test
    fun `M do nothing W required to bundle the rum information {no RUM context}`() {
        // GIVEN
        fakeFlashcatContext = fakeFlashcatContext.copy(
            featuresContext = fakeFlashcatContext.featuresContext.toMutableMap().apply {
                remove(Feature.RUM_FEATURE_NAME)
            }
        )

        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = true,
            fakeLoggerName
        )

        // THEN
        val expectedAttributes = fakeAttributes + mapOf(
            LogAttributes.DD_TRACE_ID to fakeTraceId,
            LogAttributes.DD_SPAN_ID to fakeSpanId
        )
        assertThat(log).hasExactlyAttributes(expectedAttributes)
    }

    @Test
    fun `M do nothing W not required to bundle the rum information`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = true,
            fakeLoggerName,
            bundleWithRum = false
        )

        // THEN
        val expectedAttributes = fakeAttributes + mapOf(
            LogAttributes.DD_TRACE_ID to fakeTraceId,
            LogAttributes.DD_SPAN_ID to fakeSpanId
        )
        assertThat(log).hasExactlyAttributes(expectedAttributes)
    }

    @Test
    fun `M use status CRITICAL W creating the Log { level ASSERT }`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            android.util.Log.ASSERT,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = true,
            fakeLoggerName
        )

        // THEN
        assertThat(log).hasStatus(LogEvent.Status.CRITICAL)
    }

    @Test
    fun `M use status ERROR W creating the Log { level ERROR }`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            android.util.Log.ERROR,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = true,
            fakeLoggerName
        )

        // THEN
        assertThat(log).hasStatus(LogEvent.Status.ERROR)
    }

    @Test
    fun `M use status EMERGENCY W creating the Log { level CRASH }`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            DatadogLogGenerator.CRASH,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = true,
            fakeLoggerName
        )

        // THEN
        assertThat(log).hasStatus(LogEvent.Status.EMERGENCY)
    }

    @Test
    fun `M use status WARN W creating the Log { level WARN }`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            android.util.Log.WARN,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = true,
            fakeLoggerName
        )

        // THEN
        assertThat(log).hasStatus(LogEvent.Status.WARN)
    }

    @Test
    fun `M use status INFO W creating the Log { level INFO }`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            android.util.Log.INFO,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = true,
            fakeLoggerName
        )

        // THEN
        assertThat(log).hasStatus(LogEvent.Status.INFO)
    }

    @Test
    fun `M use status DEBUG W creating the Log { level DEBUG }`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            android.util.Log.DEBUG,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = true,
            fakeLoggerName
        )

        // THEN
        assertThat(log).hasStatus(LogEvent.Status.DEBUG)
    }

    @Test
    fun `M use status TRACE W creating the Log { level VERBOSE }`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            android.util.Log.VERBOSE,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = true,
            fakeLoggerName
        )

        // THEN
        assertThat(log).hasStatus(LogEvent.Status.TRACE)
    }

    @Test
    fun `M use status DEBUG W creating the Log { other level }`(forge: Forge) {
        // WHEN
        val log = testedLogGenerator.generateLog(
            forge.anInt(min = DatadogLogGenerator.CRASH + 1),
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName,
            fakeFlashcatContext,
            attachNetworkInfo = true,
            fakeLoggerName
        )

        // THEN
        assertThat(log).hasStatus(LogEvent.Status.DEBUG)
    }
}
