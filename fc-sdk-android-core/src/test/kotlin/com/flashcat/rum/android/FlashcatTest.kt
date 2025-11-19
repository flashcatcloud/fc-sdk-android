/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import com.flashcat.rum.api.InternalLogger
import com.flashcat.rum.api.SdkCore
import com.flashcat.rum.core.configuration.Configuration
import com.flashcat.rum.core.internal.CoreFeature
import com.flashcat.rum.core.internal.FlashcatCore
import com.flashcat.rum.core.internal.HashGenerator
import com.flashcat.rum.core.internal.NoOpInternalSdkCore
import com.flashcat.rum.core.internal.SdkCoreRegistry
import com.flashcat.rum.core.internal.Sha256HashGenerator
import com.flashcat.rum.internal.utils.loggableStackTrace
import com.flashcat.rum.privacy.TrackingConsent
import com.flashcat.rum.utils.config.ApplicationContextTestConfiguration
import com.flashcat.rum.utils.config.InternalLoggerTestConfiguration
import com.flashcat.rum.utils.forge.Configurator
import com.flashcat.rum.utils.verifyLog
import com.datadog.tools.unit.annotations.ProhibitLeavingStaticMocksIn
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.ProhibitLeavingStaticMocksExtension
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.AdvancedForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.MapForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Locale

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ProhibitLeavingStaticMocksExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
@ProhibitLeavingStaticMocksIn(Flashcat::class)
internal class FlashcatTest {

    @Mock
    lateinit var mockConnectivityMgr: ConnectivityManager

    @Forgery
    lateinit var fakeConfiguration: Configuration

    @Forgery
    lateinit var fakeConsent: TrackingConsent

    @BeforeEach
    fun `set up`() {
        whenever(appContext.mockInstance.getSystemService(Context.CONNECTIVITY_SERVICE))
            .doReturn(mockConnectivityMgr)

        CoreFeature.disableKronosBackgroundSync = true
    }

    @AfterEach
    fun `tear down`() {
        Flashcat.hashGenerator = Sha256HashGenerator()
        Flashcat.stopInstance()
        Flashcat.registry.clear()
    }

    // region initialize

    @Test
    fun `M return sdk instance W initialize() + getInstance()`() {
        // When
        val initialized = Flashcat.initialize(
            appContext.mockInstance,
            fakeConfiguration,
            fakeConsent
        )
        val instance = Flashcat.getInstance()

        // Then
        assertThat(instance).isSameAs(initialized)
    }

    @Test
    fun `M return sdk instance W initialize(name) + getInstance(name)`(
        @StringForgery name: String
    ) {
        // When
        val initialized = Flashcat.initialize(
            name,
            appContext.mockInstance,
            fakeConfiguration,
            fakeConsent
        )
        val instance = Flashcat.getInstance(name)

        // Then
        assertThat(instance).isSameAs(initialized)
    }

    @Test
    fun `M warn W initialize() + initialize()`() {
        // When
        val initialized1 = Flashcat.initialize(
            appContext.mockInstance,
            fakeConfiguration,
            fakeConsent
        )
        val initialized2 = Flashcat.initialize(
            appContext.mockInstance,
            fakeConfiguration,
            fakeConsent
        )

        // Then
        logger.mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            Flashcat.MESSAGE_ALREADY_INITIALIZED
        )
        assertThat(initialized2).isSameAs(initialized1)
    }

    @Test
    fun `M warn W initialize(name) + initialize(name)`(
        @StringForgery name: String
    ) {
        // When
        Flashcat.initialize(name, appContext.mockInstance, fakeConfiguration, fakeConsent)
        Flashcat.initialize(name, appContext.mockInstance, fakeConfiguration, fakeConsent)

        // Then
        logger.mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            Flashcat.MESSAGE_ALREADY_INITIALIZED
        )
    }

    @Test
    fun `M create instance ID W initialize()`(
        @Forgery fakeConfiguration: Configuration,
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) fakeHash: String
    ) {
        // Given
        val mockHashGenerator: HashGenerator = mock()
        whenever(
            mockHashGenerator.generate(
                "null/${fakeConfiguration.coreConfig.site.siteName}"
            )
        ) doReturn fakeHash
        Flashcat.hashGenerator = mockHashGenerator

        // When
        val instance = Flashcat.initialize(
            appContext.mockInstance,
            fakeConfiguration,
            fakeConsent
        )

        // Then
        check(instance is FlashcatCore)
        assertThat(instance.instanceId).isEqualTo(fakeHash)
    }

    @Test
    fun `M create instance ID W initialize(name)`(
        @StringForgery instanceName: String,
        @Forgery fakeConfiguration: Configuration,
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) fakeHash: String
    ) {
        // Given
        val mockHashGenerator: HashGenerator = mock()
        whenever(
            mockHashGenerator.generate(
                "$instanceName/${fakeConfiguration.coreConfig.site.siteName}"
            )
        ) doReturn fakeHash
        Flashcat.hashGenerator = mockHashGenerator

        // When
        val instance = Flashcat.initialize(
            instanceName,
            appContext.mockInstance,
            fakeConfiguration,
            fakeConsent
        )

        // Then
        check(instance is FlashcatCore)
        assertThat(instance.instanceId).isEqualTo(fakeHash)
    }

    @Test
    fun `M set tracking consent W initialize()`(
        @Forgery fakeConfiguration: Configuration,
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) fakeHash: String
    ) {
        // Given
        val mockHashGenerator: HashGenerator = mock()
        whenever(
            mockHashGenerator.generate(
                "null/${fakeConfiguration.coreConfig.site.siteName}"
            )
        ) doReturn fakeHash
        Flashcat.hashGenerator = mockHashGenerator

        // When
        val instance = Flashcat.initialize(
            appContext.mockInstance,
            fakeConfiguration,
            fakeConsent
        )

        // Then
        check(instance is FlashcatCore)
        assertThat(instance.trackingConsent).isEqualTo(fakeConsent)
    }

    @Test
    fun `M set tracking consent W initialize(name)`(
        @StringForgery instanceName: String,
        @Forgery fakeConfiguration: Configuration,
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) fakeHash: String
    ) {
        // Given
        val mockHashGenerator: HashGenerator = mock()
        whenever(
            mockHashGenerator.generate(
                "$instanceName/${fakeConfiguration.coreConfig.site.siteName}"
            )
        ) doReturn fakeHash
        Flashcat.hashGenerator = mockHashGenerator

        // When
        val instance = Flashcat.initialize(
            instanceName,
            appContext.mockInstance,
            fakeConfiguration,
            fakeConsent
        )

        // Then
        check(instance is FlashcatCore)
        assertThat(instance.trackingConsent).isEqualTo(fakeConsent)
    }

    @Test
    fun `M warn W initialize() {hash generator fails}`() {
        // Given
        Flashcat.hashGenerator = mock()
        whenever(Flashcat.hashGenerator.generate(any())) doReturn null

        // When
        val instance = Flashcat.initialize(
            appContext.mockInstance,
            fakeConfiguration,
            fakeConsent
        )

        // Then
        logger.mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            Flashcat.CANNOT_CREATE_SDK_INSTANCE_ID_ERROR
        )
        assertThat(instance).isNull()
    }

    @Test
    fun `M warn W initialize(name) {hash generator fails}`(
        @StringForgery name: String
    ) {
        // Given
        Flashcat.hashGenerator = mock()
        whenever(Flashcat.hashGenerator.generate(any())) doReturn null

        // When
        val instance = Flashcat.initialize(
            name,
            appContext.mockInstance,
            fakeConfiguration,
            fakeConsent
        )

        // Then
        logger.mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            Flashcat.CANNOT_CREATE_SDK_INSTANCE_ID_ERROR
        )
        assertThat(instance).isNull()
    }

    @Test
    fun `M stop specific instance W stopInstance()`() {
        // Given
        val sdk = Flashcat.initialize(
            appContext.mockInstance,
            fakeConfiguration,
            fakeConsent
        ) as? FlashcatCore
        checkNotNull(sdk)

        // When
        Flashcat.stopInstance()
        val getInstance = Flashcat.getInstance()

        // Then
        assertThat(getInstance).isInstanceOf(NoOpInternalSdkCore::class.java)
        assertThat(sdk.coreFeature.initialized.get()).isFalse()
    }

    @Test
    fun `M stop specific instance W stopInstance(name)`(
        @StringForgery name: String
    ) {
        // Given
        val sdk = Flashcat.initialize(
            name,
            appContext.mockInstance,
            fakeConfiguration,
            fakeConsent
        ) as? FlashcatCore
        checkNotNull(sdk)

        // When
        Flashcat.stopInstance(name)
        val getInstance = Flashcat.getInstance(name)

        // Then
        assertThat(getInstance).isInstanceOf(NoOpInternalSdkCore::class.java)
        assertThat(sdk.coreFeature.initialized.get()).isFalse()
    }

    @Test
    fun `M not stop specific instance W stopInstance(name) {different name}`(
        @StringForgery name: String,
        @StringForgery name2: String
    ) {
        // Given
        val sdk = Flashcat.initialize(
            name,
            appContext.mockInstance,
            fakeConfiguration,
            fakeConsent
        ) as? FlashcatCore
        checkNotNull(sdk)

        // When
        Flashcat.stopInstance(name2)
        val getInstance = Flashcat.getInstance(name)

        // Then
        assertThat(getInstance).isSameAs(sdk)
        assertThat(sdk.coreFeature.initialized.get()).isTrue()
    }

    @Test
    fun `M warn W getInstance() { instance is not initialized }`(
        forge: Forge
    ) {
        // Given
        val fakeInstanceName = forge.aNullable { anAlphabeticalString() }

        // When
        Flashcat.getInstance(fakeInstanceName)

        // Then
        val currentMethodName = Thread.currentThread().stackTrace[1].methodName
        val expectedStacktrace = Throwable().fillInStackTrace()
            .loggableStackTrace()
            .lines()
            .drop(1)
            .filter { !it.contains(currentMethodName) }
            .joinToString(separator = "\n")
        argumentCaptor<() -> String> {
            verify(logger.mockInternalLogger).log(
                eq(InternalLogger.Level.WARN),
                eq(InternalLogger.Target.USER),
                capture(),
                isNull(),
                eq(false),
                eq(null)
            )
            val actualMessage = firstValue()
            val filteredActualMessage = actualMessage
                .lines()
                // need to filter out, because we cannot reproduce exactly the same stacktrace
                .filter { !it.contains(currentMethodName) && !it.contains("getInstance") }
                .joinToString(separator = "\n")
            assertThat(filteredActualMessage)
                .isEqualTo(
                    Flashcat.MESSAGE_SDK_NOT_INITIALIZED.format(
                        Locale.US,
                        fakeInstanceName ?: SdkCoreRegistry.DEFAULT_INSTANCE_NAME,
                        expectedStacktrace
                    )
                )
        }
    }

    @Test
    fun `M return false W isInitialized() { instance is not initialized }`(
        forge: Forge
    ) {
        // Given
        val fakeInstanceName = forge.aNullable { anAlphabeticalString() }

        // When
        val result = Flashcat.isInitialized(fakeInstanceName)

        // Then
        assertThat(result).isFalse
        verifyNoInteractions(logger.mockInternalLogger)
    }

    @Test
    fun `M return true W isInitialized() { instance is initialized }`(
        forge: Forge
    ) {
        // Given
        val fakeInstanceName = forge.aNullable { anAlphabeticalString() }

        Flashcat.initialize(
            fakeInstanceName,
            appContext.mockInstance,
            fakeConfiguration,
            fakeConsent
        )

        // When
        val result = Flashcat.isInitialized(fakeInstanceName)

        // Then
        assertThat(result).isTrue()
        verifyNoInteractions(logger.mockInternalLogger)
    }

    // endregion

    @Test
    fun `M set and get lib verbosity W setVerbosity() + getVerbosity()`(
        @IntForgery level: Int
    ) {
        // When
        Flashcat.setVerbosity(level)
        val result = Flashcat.getVerbosity()

        // Then
        assertThat(result).isEqualTo(level)
    }

    @Test
    fun `M do nothing W stop() without initialize`() {
        // When
        Flashcat.stopInstance()

        // Then
        verifyNoInteractions(appContext.mockInstance)
    }

    @Test
    fun `M set tracking consent W setTrackingConsent()`(
        @Forgery fakeTrackingConsent: TrackingConsent
    ) {
        // Given
        val mockSdkCore = mock<SdkCore>()

        // When
        Flashcat.setTrackingConsent(fakeTrackingConsent, mockSdkCore)

        // Then
        verify(mockSdkCore).setTrackingConsent(fakeTrackingConsent)
    }

    @Test
    fun `M set user info W setUserInfo()`(
        @StringForgery(type = StringForgeryType.HEXADECIMAL) id: String,
        @StringForgery name: String,
        @StringForgery(regex = "\\w+@\\w+") email: String,
        @MapForgery(
            key = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHA_NUMERICAL)]),
            value = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHA_NUMERICAL)])
        ) fakeUserProperties: Map<String, String>
    ) {
        // Given
        val mockSdkCore = mock<SdkCore>()

        // When
        Flashcat.setUserInfo(id, name, email, fakeUserProperties, mockSdkCore)

        // Then
        verify(mockSdkCore).setUserInfo(id, name, email, fakeUserProperties)
    }

    @Test
    fun `M add user properties W addUserProperties()`(
        @MapForgery(
            key = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHA_NUMERICAL)]),
            value = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHA_NUMERICAL)])
        ) fakeUserProperties: Map<String, String>
    ) {
        // Given
        val mockSdkCore = mock<SdkCore>()

        // When
        Flashcat.addUserProperties(fakeUserProperties, mockSdkCore)

        // Then
        verify(mockSdkCore).addUserProperties(fakeUserProperties)
    }

    @Test
    fun `M clear user info W clearUserInfo()`() {
        // Given
        val mockSdkCore = mock<SdkCore>()

        // When
        Flashcat.clearUserInfo(mockSdkCore)

        // Then
        verify(mockSdkCore).clearUserInfo()
    }

    @Test
    fun `M clear all data W clearAllData()`() {
        // Given
        val mockSdkCore = mock<SdkCore>()

        // When
        Flashcat.clearAllData(mockSdkCore)

        // Then
        verify(mockSdkCore).clearAllData()
    }

    @Test
    fun `M call Core set account info W setAccountInfo()`(
        @StringForgery(type = StringForgeryType.HEXADECIMAL) id: String,
        @StringForgery name: String,
        @MapForgery(
            key = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHA_NUMERICAL)]),
            value = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHA_NUMERICAL)])
        ) fakeExtraInfo: Map<String, String>
    ) {
        // Given
        val mockSdkCore = mock<SdkCore>()

        // When
        Flashcat.setAccountInfo(
            id = id,
            name = name,
            extraInfo = fakeExtraInfo,
            sdkCore = mockSdkCore
        )

        // Then
        verify(mockSdkCore).setAccountInfo(id, name, fakeExtraInfo)
    }

    @Test
    fun `M call Core add account extra info W addAccountExtraInfo()`(
        @MapForgery(
            key = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHA_NUMERICAL)]),
            value = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHA_NUMERICAL)])
        ) fakeExtraInfo: Map<String, String>
    ) {
        // Given
        val mockSdkCore = mock<SdkCore>()

        // When
        Flashcat.addAccountExtraInfo(
            extraInfo = fakeExtraInfo,
            sdkCore = mockSdkCore
        )

        // Then
        verify(mockSdkCore).addAccountExtraInfo(fakeExtraInfo)
    }

    @Test
    fun `M call Core clear account info W clearAccountInfo()`() {
        // Given
        val mockSdkCore = mock<SdkCore>()

        // When
        Flashcat.clearAccountInfo(sdkCore = mockSdkCore)

        // Then
        verify(mockSdkCore).clearAccountInfo()
    }
    companion object {
        val appContext = ApplicationContextTestConfiguration(Application::class.java)
        val logger = InternalLoggerTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(logger, appContext)
        }
    }
}
