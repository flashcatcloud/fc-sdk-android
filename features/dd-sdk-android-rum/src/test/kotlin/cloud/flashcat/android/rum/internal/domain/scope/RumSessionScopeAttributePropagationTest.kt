/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.rum.internal.domain.scope

import cloud.flashcat.android.api.InternalLogger
import cloud.flashcat.android.api.context.DatadogContext
import cloud.flashcat.android.api.context.NetworkInfo
import cloud.flashcat.android.api.feature.FeatureScope
import cloud.flashcat.android.api.storage.DataWriter
import cloud.flashcat.android.api.storage.EventBatchWriter
import cloud.flashcat.android.core.InternalSdkCore
import cloud.flashcat.android.core.internal.net.FirstPartyHostHeaderTypeResolver
import cloud.flashcat.android.rum.RumSessionListener
import cloud.flashcat.android.rum.RumSessionType
import cloud.flashcat.android.rum.internal.FeaturesContextResolver
import cloud.flashcat.android.rum.internal.domain.InfoProvider
import cloud.flashcat.android.rum.internal.domain.RumContext
import cloud.flashcat.android.rum.internal.domain.Time
import cloud.flashcat.android.rum.internal.domain.accessibility.AccessibilitySnapshotManager
import cloud.flashcat.android.rum.internal.domain.battery.BatteryInfo
import cloud.flashcat.android.rum.internal.domain.display.DisplayInfo
import cloud.flashcat.android.rum.internal.metric.SessionMetricDispatcher
import cloud.flashcat.android.rum.internal.metric.slowframes.SlowFramesListener
import cloud.flashcat.android.rum.internal.startup.RumAppStartupTelemetryReporter
import cloud.flashcat.android.rum.internal.vitals.VitalMonitor
import cloud.flashcat.android.rum.metric.interactiontonextview.LastInteractionIdentifier
import cloud.flashcat.android.rum.metric.networksettled.InitialResourceIdentifier
import cloud.flashcat.android.rum.utils.forge.Configurator
import cloud.flashcat.tools.unit.forge.exhaustiveAttributes
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumSessionScopeAttributePropagationTest {

    lateinit var testedScope: RumSessionScope

    @Mock
    lateinit var mockSdkCore: InternalSdkCore

    @Mock
    lateinit var mockParentScope: RumScope

    @Mock
    lateinit var mockWriter: DataWriter<Any>

    @Mock
    lateinit var mockResolver: FirstPartyHostHeaderTypeResolver

    @Mock
    lateinit var mockSessionListener: RumSessionListener

    @Mock
    lateinit var mockNetworkSettledResourceIdentifier: InitialResourceIdentifier

    @Mock
    lateinit var mockLastInteractionIdentifier: LastInteractionIdentifier

    @Mock
    lateinit var mockCpuVitalMonitor: VitalMonitor

    @Mock
    lateinit var mockMemoryVitalMonitor: VitalMonitor

    @Mock
    lateinit var mockFrameRateVitalMonitor: VitalMonitor

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockAccessibilitySnapshotManager: AccessibilitySnapshotManager

    @Mock
    lateinit var mockBatteryInfoProvider: InfoProvider<BatteryInfo>

    @Mock
    lateinit var mockDisplayInfoProvider: InfoProvider<DisplayInfo>

    @Mock
    lateinit var mockRumAppStartupTelemetryReporter: RumAppStartupTelemetryReporter

    @Mock
    lateinit var mockRumFeatureScope: FeatureScope

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    @Mock
    lateinit var mockFeaturesContextResolver: FeaturesContextResolver

    @Mock
    lateinit var mockViewChangedListener: RumViewChangedListener

    @Mock
    lateinit var mockSessionEndedMetricDispatcher: SessionMetricDispatcher

    @Mock
    lateinit var mockSlowFramesListener: SlowFramesListener

    lateinit var fakeParentAttributes: Map<String, Any?>

    @Forgery
    lateinit var fakeParentContext: RumContext

    lateinit var fakeEventTime: Time

    lateinit var fakeEvent: RumRawEvent

    @Forgery
    lateinit var fakeNetworkInfoAtScopeStart: NetworkInfo

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @BoolForgery
    var fakeHasReplay: Boolean = false

    @FloatForgery(min = 0f, max = 100f)
    var fakeSampleRate: Float = 0f

    @BoolForgery
    var fakeBackgroundTrackingEnabled: Boolean = false

    @BoolForgery
    var fakeTrackFrustrations: Boolean = true
    private var fakeRumSessionType: RumSessionType? = null

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeParentAttributes = forge.exhaustiveAttributes()
        whenever(mockParentScope.getCustomAttributes()) doReturn fakeParentAttributes.toMutableMap()

        whenever(mockSdkCore.internalLogger) doReturn mock()
        fakeRumSessionType = forge.aNullable { aValueFrom(RumSessionType::class.java) }
        testedScope = RumSessionScope(
            parentScope = mockParentScope,
            sdkCore = mockSdkCore,
            sessionEndedMetricDispatcher = mockSessionEndedMetricDispatcher,
            sampleRate = fakeSampleRate,
            backgroundTrackingEnabled = fakeBackgroundTrackingEnabled,
            trackFrustrations = fakeTrackFrustrations,
            viewChangedListener = mockViewChangedListener,
            firstPartyHostHeaderTypeResolver = mockResolver,
            cpuVitalMonitor = mockCpuVitalMonitor,
            memoryVitalMonitor = mockMemoryVitalMonitor,
            frameRateVitalMonitor = mockFrameRateVitalMonitor,
            sessionListener = mockSessionListener,
            applicationDisplayed = false,
            networkSettledResourceIdentifier = mockNetworkSettledResourceIdentifier,
            lastInteractionIdentifier = mockLastInteractionIdentifier,
            slowFramesListener = mockSlowFramesListener,
            sessionInactivityNanos = TEST_INACTIVITY_NS,
            sessionMaxDurationNanos = TEST_MAX_DURATION_NS,
            rumSessionTypeOverride = fakeRumSessionType,
            accessibilitySnapshotManager = mockAccessibilitySnapshotManager,
            batteryInfoProvider = mockBatteryInfoProvider,
            displayInfoProvider = mockDisplayInfoProvider,
            rumAppStartupTelemetryReporter = mockRumAppStartupTelemetryReporter
        )
    }

    // region Propagate parent attributes

    @Test
    fun `M return parent attributes W getCustomAttributes()`() {
        // When
        val customAttributes = testedScope.getCustomAttributes()

        // Then
        assertThat(customAttributes)
            .containsExactlyInAnyOrderEntriesOf(fakeParentAttributes)
    }

    // endregion

    companion object {
        private const val TEST_SLEEP_MS = 50L
        private const val TEST_INACTIVITY_MS = TEST_SLEEP_MS * 3
        private const val TEST_MAX_DURATION_MS = TEST_SLEEP_MS * 10

        private val TEST_INACTIVITY_NS = TimeUnit.MILLISECONDS.toNanos(TEST_INACTIVITY_MS)
        private val TEST_MAX_DURATION_NS = TimeUnit.MILLISECONDS.toNanos(TEST_MAX_DURATION_MS)
    }
}
