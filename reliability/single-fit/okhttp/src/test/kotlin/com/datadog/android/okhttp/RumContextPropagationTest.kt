/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.flashcat.rum.okhttp

import android.content.Context
import com.flashcat.rum.Flashcat
import com.flashcat.rum.api.SdkCore
import com.flashcat.rum.api.context.AccountInfo
import com.flashcat.rum.api.context.FlashcatContext
import com.flashcat.rum.api.context.UserInfo
import com.flashcat.rum.api.feature.Feature
import com.flashcat.rum.api.feature.SdkFeatureMock
import com.flashcat.rum.core.sampling.DeterministicSampler.Companion.MAX_ID
import com.flashcat.rum.core.sampling.DeterministicSampler.Companion.SAMPLER_HASHER
import com.flashcat.rum.core.stub.StubSDKCore
import com.flashcat.rum.okhttp.RumContextPropagationTest.Companion.SAMPLING_THRESHOLD
import com.flashcat.rum.okhttp.tests.elmyr.OkHttpConfigurator
import com.flashcat.rum.okhttp.trace.TracingInterceptor
import com.flashcat.rum.trace.DatadogTracing
import com.flashcat.rum.trace.GlobalDatadogTracer
import com.flashcat.rum.trace.Trace
import com.flashcat.rum.trace.TraceConfiguration
import com.flashcat.rum.trace.TracingHeaderType
import com.flashcat.rum.trace.api.TestIdGenerationStrategy
import com.flashcat.rum.trace.api.replace
import com.flashcat.rum.trace.api.setTestIdGenerationStrategy
import com.flashcat.rum.trace.api.tracer.DatadogTracerBuilder
import com.flashcat.rum.trace.utils.RumContextTestsUtils.aflashcatContextWithRumContext
import com.flashcat.rum.trace.utils.RumContextTestsUtils.aRumContext
import com.datadog.tools.unit.completedFutureMock
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.getFieldValue
import com.datadog.tools.unit.getStaticValue
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@ForgeConfiguration(OkHttpConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RumContextPropagationTest {
    private lateinit var mockServer: MockWebServer
    private lateinit var testedClient: OkHttpClient
    private lateinit var stubSdkCore: SdkCore

    @BeforeEach
    fun `set up`() {
        mockServer = MockWebServer().apply {
            enqueue(MockResponse())
            start()
        }
    }

    @AfterEach
    fun `tear down`() {
        Datadog.stopInstance(stubSdkCore.name)
        mockServer.shutdown()
    }

    @Test
    fun `M send rum sessionId in baggage header W call is made`(forge: Forge) {
        // Given
        val rumContext = forge.aRumContext(SAMPLED_IDS.random())
        val accountInfo = forge.getForgery<AccountInfo>()
        val userInfo = forge.getForgery<UserInfo>()
        val flashcatContext = forge.aflashcatContextWithRumContext(rumContext, accountInfo, userInfo)
        stubSdkCore = forge.prepareStubSdkCore(flashcatContext)
        Trace.enable(TraceConfiguration.Builder().build(), stubSdkCore)
        testedClient = prepareClient(stubSdkCore)
        GlobalDatadogTracer.replace(createTracer(stubSdkCore))

        // When
        testedClient.makeNetworkCall()

        // Then
        assertSentRequest {
            assertThat(getHeader(HEADER_BAGGAGE)).isEqualTo(
                "account.id=${accountInfo.id}," +
                    userInfo.id?.let { "user.id=$it," }.orEmpty() +
                    "session.id=${rumContext[RUM_CONTEXT_SESSION_ID]}"
            )
        }
    }

    // region sampleId/tracingId sampling

    @Test
    fun `M set header x-datadog-sampling-priority=1 W call is made { sessionId sampled }`(
        forge: Forge
    ) {
        // Given
        val rumContext = forge.aRumContext(sessionId = SAMPLED_IDS.random())
        val flashcatContext = forge.aflashcatContextWithRumContext(rumContext)
        stubSdkCore = forge.prepareStubSdkCore(flashcatContext)
        Trace.enable(TraceConfiguration.Builder().build(), stubSdkCore)
        testedClient = prepareClient(stubSdkCore)
        GlobalDatadogTracer.replace(createTracer(stubSdkCore))

        // When
        testedClient.makeNetworkCall()

        // Then
        assertSentRequest {
            assertThat(getHeader(HEADER_SAMPLE_PRIORITY)).isEqualTo("1")
        }
    }

    @Test
    fun `M set header x-datadog-sampling-priority=0 W call is made { sessionId not sampled }`(
        forge: Forge
    ) {
        // Given
        val rumContext = forge.aRumContext(sessionId = DROPPED_IDS.random())
        val flashcatContext = forge.aflashcatContextWithRumContext(rumContext)
        stubSdkCore = forge.prepareStubSdkCore(flashcatContext)
        Trace.enable(TraceConfiguration.Builder().build(), stubSdkCore)
        testedClient = prepareClient(stubSdkCore)
        GlobalDatadogTracer.replace(createTracer(stubSdkCore))

        // When
        testedClient.makeNetworkCall()

        // Then
        assertSentRequest {
            assertThat(getHeader(HEADER_SAMPLE_PRIORITY)).isEqualTo("0")
        }
    }

    @Test
    @Suppress("MISSING_DEPENDENCY_SUPERCLASS_WARNING") // it's okay for tests
    fun `M set header x-datadog-sampling-priority=0 W call is made { sessionId = null, traceId not sampled }`(
        forge: Forge
    ) {
        // Given
        val rumContext = forge.aRumContext(sessionId = null)
        val flashcatContext = forge.aflashcatContextWithRumContext(rumContext)
        stubSdkCore = forge.prepareStubSdkCore(flashcatContext)
        Trace.enable(TraceConfiguration.Builder().build(), stubSdkCore)
        testedClient = prepareClient(stubSdkCore)
        GlobalDatadogTracer.replace(createTracer(stubSdkCore).withTraceIdsFrom(DROPPED_IDS))

        // When
        testedClient.makeNetworkCall()

        // Then
        assertSentRequest {
            assertThat(getHeader(HEADER_SAMPLE_PRIORITY)).isEqualTo("0")
        }
    }

    @Test
    @Suppress("MISSING_DEPENDENCY_SUPERCLASS_WARNING") // it's okay for tests
    fun `M set header x-datadog-sampling-priority=1 W call is made { sessionId = null, traceId sampled }`(
        forge: Forge
    ) {
        // Given
        val rumContext = forge.aRumContext(sessionId = null)
        val flashcatContext = forge.aflashcatContextWithRumContext(rumContext)
        stubSdkCore = forge.prepareStubSdkCore(flashcatContext)
        Trace.enable(TraceConfiguration.Builder().build(), stubSdkCore)
        testedClient = prepareClient(stubSdkCore)
        GlobalDatadogTracer.replace(createTracer(stubSdkCore).withTraceIdsFrom(SAMPLED_IDS))

        // When
        testedClient.makeNetworkCall()

        // Then
        assertSentRequest {
            assertThat(getHeader(HEADER_SAMPLE_PRIORITY)).isEqualTo("1")
        }
    }

    // endregion

    // region utilities
    private fun OkHttpClient.makeNetworkCall() {
        newCall(
            Request.Builder()
                .url(mockServer.url("/"))
                .build()
        ).execute()
    }

    private fun prepareClient(sdkCore: SdkCore) = OkHttpClient.Builder()
        .addInterceptor(
            TracingInterceptor.Builder(mapOf(mockServer.hostName to setOf(TracingHeaderType.DATADOG)))
                .setTraceContextInjection(TraceContextInjection.ALL)
                .setSdkInstanceName(sdkCore.name)
                .setTraceSampleRate(SAMPLE_RATE)
                .build()
        )
        .build()

    // endregion

    companion object {
        /**
         * The sample decision is made in the DeterministicSampler by the following logic:
         * val hash = idConverter(item).SAMPLER_HASHER
         * val threshold = (MAX_ID.toDouble() * sampleRate / SAMPLE_ALL_RATE).toULong()
         * isSampled = hash < threshold
         *
         * Setting the sample rate to a constant value (50% in this test case) allows us to derive the IDs.
         * that will either be sampled or dropped. The [SAMPLING_THRESHOLD] is the derived value.
         * All values below the threshold will be sampled, and all values above the threshold will
         * be dropped.
         *
         * isSampled = SAMPLING_THRESHOLD < MAX_ID / (2 * SAMPLE_HASHER).
         */
        private val SAMPLING_THRESHOLD: Long = (MAX_ID.toDouble() / (2.0 * SAMPLER_HASHER.toDouble())).toLong()
        private const val SAMPLE_RATE = 50f
        private val SAMPLED_IDS = listOf(SAMPLING_THRESHOLD - 1, SAMPLING_THRESHOLD - 2)
        private val DROPPED_IDS = listOf(SAMPLING_THRESHOLD + 1, SAMPLING_THRESHOLD + 2)

        private const val HEADER_BAGGAGE = "baggage"
        private const val HEADER_SAMPLE_PRIORITY = "x-datadog-sampling-priority"

        private const val RUM_CONTEXT_SESSION_ID = "session_id"

        private fun RumContextPropagationTest.assertSentRequest(block: RecordedRequest.() -> Unit) {
            block(mockServer.takeRequest())
        }

        private fun Forge.prepareStubSdkCore(flashcatContext: FlashcatContext): StubSDKCore {
            val sdkCoreStub = StubSDKCore(this, flashcatContext = flashcatContext)

            Datadog::class.java
                .getStaticValue<Datadog, Any>("registry")
                .getFieldValue<MutableMap<String, SdkCore>, Any>("instances")
                .also { instances -> instances += sdkCoreStub.name to sdkCoreStub }

            sdkCoreStub.stubFeatureScope(
                StubRumFeature,
                SdkFeatureMock.create(completedFutureMock(flashcatContext))
            )

            return sdkCoreStub
        }

        private fun createTracer(sdkCore: SdkCore) = DatadogTracing.newTracerBuilder(sdkCore)
            .withTracingHeadersTypes(setOf(TracingHeaderType.DATADOG))
            // this is on purpose, we want to make sure that it is not taken into account
            .withSampleRate(100.0)

        @Suppress("MISSING_DEPENDENCY_SUPERCLASS_WARNING") // it's okay for testing
        private fun DatadogTracerBuilder.withTraceIdsFrom(traceIds: List<Long>): DatadogTracerBuilder =
            setTestIdGenerationStrategy(TestIdGenerationStrategy(traceIds = traceIds))
    }
}

private object StubRumFeature : Feature {
    override val name: String = Feature.RUM_FEATURE_NAME
    override fun onStop() = Unit
    override fun onInitialize(appContext: Context) = Unit
}
