/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import android.app.Activity
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.internal.startup.RumStartupScenario
import com.datadog.android.rum.internal.startup.RumTTIDInfo
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.DoubleForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.assertj.core.api.Assertions.assertThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock as kmock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumInternalProxyTest {

    @Test
    fun `M proxy addLongTask to RumMonitor W addLongTask()`(
        @LongForgery time: Long,
        @StringForgery target: String
    ) {
        // Given
        val mockRumMonitor = mock(AdvancedRumMonitor::class.java)
        val proxy = _RumInternalProxy(mockRumMonitor, kmock<InternalSdkCore>())

        // When
        proxy.addLongTask(time, target)

        // Then
        verify(mockRumMonitor).addLongTask(time, target)
    }

    @Test
    fun `M proxy updatePerformanceMetric to RumMonitor W updatePerformanceMetric()`(
        forge: Forge
    ) {
        // Given
        val metric = forge.aValueFrom(RumPerformanceMetric::class.java)
        val value = forge.aDouble()
        val mockRumMonitor = mock(AdvancedRumMonitor::class.java)
        val proxy = _RumInternalProxy(mockRumMonitor, kmock<InternalSdkCore>())

        // When
        proxy.updatePerformanceMetric(metric, value)

        // Then
        verify(mockRumMonitor).updatePerformanceMetric(metric, value)
    }

    @Test
    fun `M proxy updateExternalRefreshRate to RumMonitor W updateExternalRefreshRate()`(
        @DoubleForgery(min = 0.001, max = 1.0) frameTimeSeconds: Double
    ) {
        // Given
        val mockRumMonitor = mock(AdvancedRumMonitor::class.java)
        val proxy = _RumInternalProxy(mockRumMonitor, kmock<InternalSdkCore>())

        // When
        proxy.updateExternalRefreshRate(frameTimeSeconds)

        // Then
        verify(mockRumMonitor).updateExternalRefreshRate(frameTimeSeconds)
    }

    @Test
    fun `M proxy enableJankStatsTracking to RumMonitor W enableJankStatsTracking()`() {
        // Given
        val mockRumMonitor = mock(AdvancedRumMonitor::class.java)
        val proxy = _RumInternalProxy(mockRumMonitor, kmock<InternalSdkCore>())
        val activity: Activity = mock()

        // When
        proxy.enableJankStatsTracking(activity)

        // Then
        verify(mockRumMonitor).enableJankStatsTracking(activity)
    }

    @Test
    fun `M send the app start event before the TTID event W notifyAppLaunchIfAbsent()`() {
        // Given - the session scope numbers a launch from the app start event, so a TTID
        // event arriving without one would be recorded against no scenario at all.
        val mockRumMonitor = mock(AdvancedRumMonitor::class.java)
        val mockSdkCore = stubSdkCore(elapsedNs = TimeUnit.SECONDS.toNanos(2), fallbackClaimed = true)
        val proxy = _RumInternalProxy(mockRumMonitor, mockSdkCore)

        // When
        proxy.notifyAppLaunchIfAbsent(0L, 0L)

        // Then
        val scenarioCaptor = argumentCaptor<RumStartupScenario>()
        verify(mockRumMonitor).sendAppStartEvent(scenarioCaptor.capture())
        verify(mockRumMonitor).sendTTIDEvent(argumentCaptor<RumTTIDInfo>().capture())
        assertThat(scenarioCaptor.firstValue).isInstanceOf(RumStartupScenario.Cold::class.java)
    }

    @Test
    fun `M stop the duration at the launch frame W notifyAppLaunchIfAbsent() {frame end offset}`() {
        // Given
        val mockRumMonitor = mock(AdvancedRumMonitor::class.java)
        val elapsedNs = TimeUnit.SECONDS.toNanos(2)
        val frameEndOffsetNs = TimeUnit.MILLISECONDS.toNanos(200)
        val mockSdkCore = stubSdkCore(elapsedNs = elapsedNs, fallbackClaimed = true)
        val proxy = _RumInternalProxy(mockRumMonitor, mockSdkCore)

        // When
        proxy.notifyAppLaunchIfAbsent(0L, frameEndOffsetNs)

        // Then - the launch frame finished before the call reached us, so the reported
        // duration has to be shorter than the time elapsed since process start.
        val infoCaptor = argumentCaptor<RumTTIDInfo>()
        verify(mockRumMonitor).sendTTIDEvent(infoCaptor.capture())
        assertThat(infoCaptor.firstValue.durationNs)
            .isLessThan(elapsedNs)
            .isGreaterThan(elapsedNs - frameEndOffsetNs - TimeUnit.MILLISECONDS.toNanos(500))
    }

    @Test
    fun `M report nothing W notifyAppLaunchIfAbsent() {native detector already reported}`() {
        // Given - the host cannot tell whether the native detector saw this launch, so it asks
        // unconditionally and the SDK declines when the detector already owns it.
        val mockRumMonitor = mock(AdvancedRumMonitor::class.java)
        val mockSdkCore = stubSdkCore(elapsedNs = TimeUnit.SECONDS.toNanos(2), fallbackClaimed = false)
        val proxy = _RumInternalProxy(mockRumMonitor, mockSdkCore)

        // When
        proxy.notifyAppLaunchIfAbsent(0L, 0L)

        // Then
        verifyNoInteractions(mockRumMonitor)
    }

    private fun stubSdkCore(elapsedNs: Long, fallbackClaimed: Boolean): InternalSdkCore {
        val mockRumFeature = kmock<RumFeature>()
        whenever(mockRumFeature.claimAppLaunchFallback()) doReturn fallbackClaimed
        val mockScope = kmock<FeatureScope>()
        whenever(mockScope.unwrap<RumFeature>()) doReturn mockRumFeature
        val mockSdkCore = kmock<InternalSdkCore>()
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockScope
        whenever(mockSdkCore.appStartTimeNs) doReturn System.nanoTime() - elapsedNs
        return mockSdkCore
    }
}
