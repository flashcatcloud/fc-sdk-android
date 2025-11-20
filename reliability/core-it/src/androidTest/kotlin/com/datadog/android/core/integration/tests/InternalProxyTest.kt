/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.core.integration.tests

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flashcat.rum.Flashcat
import com.flashcat.rum.core.InternalSdkCore
import com.flashcat.rum.core.configuration.Configuration
import com.flashcat.rum.core.integration.tests.forge.factories.ConfigurationCoreForgeryFactory
import com.flashcat.rum.privacy.TrackingConsent
import com.flashcat.tools.unit.forge.useToolsFactories
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit4.ForgeRule
import fr.xgouchet.elmyr.jvm.useJvmFactories
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Provides the tests for the InternalProxy API.
 */
@RunWith(AndroidJUnit4::class)
class InternalProxyTest {

    private lateinit var testedInternalSdkCore: InternalSdkCore

    @Forgery
    lateinit var fakeConfiguration: Configuration

    @get:Rule
    var forge = ForgeRule()
        .useJvmFactories()
        .useToolsFactories()
        .withFactory(ConfigurationCoreForgeryFactory())

    @Before
    fun setUp() {
        testedInternalSdkCore = Datadog.initialize(
            ApplicationProvider.getApplicationContext(),
            fakeConfiguration,
            forge.aValueFrom(TrackingConsent::class.java)
        ) as InternalSdkCore
    }

    @After
    fun tearDown() {
        Datadog.stopInstance()
    }

    // region set version

    @Test
    fun mustSetAppVersion_when_setCustomAppVersion() {
        // Given
        val fakeAppVersion = forge.anAlphabeticalString()
        val internalProxy = Datadog._internalProxy()

        // When
        internalProxy.setCustomAppVersion(fakeAppVersion)

        // Then
        val context = testedInternalSdkCore.getFlashcatContext()
        checkNotNull(context)
        assertThat(context.version).isEqualTo(fakeAppVersion)
    }

    // endregion
}
