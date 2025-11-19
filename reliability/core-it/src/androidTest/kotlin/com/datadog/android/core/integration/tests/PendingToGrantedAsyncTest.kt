/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.core.integration.tests

import androidx.test.core.app.ApplicationProvider
import com.flashcat.rum.Flashcat
import com.flashcat.rum.api.feature.Feature
import com.flashcat.rum.api.feature.stub.StubStorageBackedFeature
import com.flashcat.rum.api.storage.EventType
import com.flashcat.rum.api.storage.RawBatchEvent
import com.flashcat.rum.core.InternalSdkCore
import com.flashcat.rum.core.configuration.Configuration
import com.flashcat.rum.core.integration.tests.assertj.MockWebServerAssert
import com.flashcat.rum.core.integration.tests.forge.factories.ConfigurationCoreForgeryFactory
import com.flashcat.rum.privacy.TrackingConsent
import com.datadog.tools.unit.ConditionWatcher
import com.datadog.tools.unit.forge.useToolsFactories
import com.google.gson.JsonObject
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit4.ForgeRule
import fr.xgouchet.elmyr.jvm.useJvmFactories
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Instrumentation tests for the feature scope for the case when the tracking consent is switched from Pending to
 * Granted. These tests are meant to assess the correct behavior of our persistence strategy
 * when write events are sent to the feature scope and the tracking consent is switched from Pending to Granted from
 * 2 different threads.
 */
@RunWith(Parameterized::class)
class PendingToGrantedAsyncTest(
    private val fakeBatchData: List<RawBatchEvent>,
    private val fakeBatchMetadata: ByteArray,
    private val fakeConfiguration: Configuration,
    private val eventType: EventType
) : MockServerTest() {

    @get:Rule
    var forge = ForgeRule()
        .useJvmFactories()
        .useToolsFactories()
        .withFactory(ConfigurationCoreForgeryFactory())

    @StringForgery(type = StringForgeryType.ALPHABETICAL)
    lateinit var fakeFeatureName: String

    private lateinit var trackingConsent: TrackingConsent
    private lateinit var stubFeature: Feature
    private lateinit var testedInternalSdkCore: InternalSdkCore

    @Before
    fun setUp() {
        stubFeature = StubStorageBackedFeature(
            forge,
            fakeFeatureName,
            getMockServerWrapper().getServerUrl()
        )
    }

    @After
    fun tearDown() {
        cleanStorage()
        Datadog.stopInstance()
        cleanMockWebServer()
    }

    @Test
    fun mustReceiveTheEvents_whenFeatureWrite_asynchronousAccess() {
        // Given
        trackingConsent = TrackingConsent.PENDING
        testedInternalSdkCore = Datadog.initialize(
            context = ApplicationProvider.getApplicationContext(),
            configuration = fakeConfiguration,
            trackingConsent = trackingConsent
        ) as InternalSdkCore
        testedInternalSdkCore.registerFeature(stubFeature)
        val featureScope = testedInternalSdkCore.getFeature(fakeFeatureName)
        checkNotNull(featureScope)
        val countDownLatch = CountDownLatch(2)

        // When
        Thread {
            Thread.sleep(200)
            Datadog.setTrackingConsent(TrackingConsent.GRANTED)
            countDownLatch.countDown()
        }.start()
        Thread {
            fakeBatchData.forEach { rawBatchEvent ->
                featureScope.withWriteContext { _, writeScope ->
                    writeScope {
                        it.write(
                            rawBatchEvent,
                            fakeBatchMetadata,
                            eventType
                        )
                    }
                }
            }
            countDownLatch.countDown()
        }.start()

        // Then
        countDownLatch.await(TimeUnit.SECONDS.toMillis(10), TimeUnit.MILLISECONDS)
        ConditionWatcher {
            MockWebServerAssert.assertThat(getMockServerWrapper())
                .withConfiguration(fakeConfiguration)
                .withTrackingConsent(TrackingConsent.GRANTED)
                .receivedData(fakeBatchData, fakeBatchMetadata)
            true
        }.doWait(MEDIUM_WAIT_MS)
    }

    // region Internal

    private fun cleanStorage() {
        val storageFolder = testedInternalSdkCore.rootStorageDir
        storageFolder?.deleteRecursively()
    }

    companion object {
        private val forge = ForgeRule()
            .useJvmFactories()
            .useToolsFactories()
            .withFactory(ConfigurationCoreForgeryFactory())

        @JvmStatic
        @Parameters
        fun testParameters(): Collection<Array<Any>> {
            return listOf(
                arrayOf(
                    forge.aList(size = forge.anInt(min = 50, max = 100)) {
                        val fakeEvent: JsonObject = forge.getForgery()
                        val eventMetadata = forge.anAlphabeticalString()
                        RawBatchEvent(
                            fakeEvent.toString().toByteArray(),
                            eventMetadata.toByteArray()
                        )
                    },
                    forge.anAlphabeticalString().toByteArray(),
                    forge.getForgery<Configuration>(),
                    forge.aValueFrom(EventType::class.java)
                ),
                arrayOf(
                    forge.aList(size = forge.anInt(min = 50, max = 100)) {
                        val fakeEvent: JsonObject = forge.getForgery()
                        val eventMetadata = forge.anAlphabeticalString()
                        RawBatchEvent(
                            fakeEvent.toString().toByteArray(),
                            eventMetadata.toByteArray()
                        )
                    },
                    forge.anAlphabeticalString().toByteArray(),
                    forge.getForgery<Configuration>(),
                    forge.aValueFrom(EventType::class.java)
                ),
                arrayOf(
                    forge.aList(size = forge.anInt(min = 50, max = 100)) {
                        val fakeEvent: JsonObject = forge.getForgery()
                        val eventMetadata = forge.anAlphabeticalString()
                        RawBatchEvent(
                            fakeEvent.toString().toByteArray(),
                            eventMetadata.toByteArray()
                        )
                    },
                    forge.anAlphabeticalString().toByteArray(),
                    forge.getForgery<Configuration>(),
                    forge.aValueFrom(EventType::class.java)
                ),
                arrayOf(
                    forge.aList(size = forge.anInt(min = 50, max = 100)) {
                        val fakeEvent: JsonObject = forge.getForgery()
                        val eventMetadata = forge.anAlphabeticalString()
                        RawBatchEvent(
                            fakeEvent.toString().toByteArray(),
                            eventMetadata.toByteArray()
                        )
                    },
                    forge.anAlphabeticalString().toByteArray(),
                    forge.getForgery<Configuration>(),
                    forge.aValueFrom(EventType::class.java)
                ),
                arrayOf(
                    forge.aList(size = forge.anInt(min = 50, max = 100)) {
                        val fakeEvent: JsonObject = forge.getForgery()
                        val eventMetadata = forge.anAlphabeticalString()
                        RawBatchEvent(
                            fakeEvent.toString().toByteArray(),
                            eventMetadata.toByteArray()
                        )
                    },
                    forge.anAlphabeticalString().toByteArray(),
                    forge.getForgery<Configuration>(),
                    forge.aValueFrom(EventType::class.java)
                )
            )
        }
    }

    // endregion
}
