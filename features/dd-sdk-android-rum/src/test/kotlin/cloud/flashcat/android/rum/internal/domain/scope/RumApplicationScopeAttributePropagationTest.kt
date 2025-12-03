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
import cloud.flashcat.android.api.context.TimeInfo
import cloud.flashcat.android.api.feature.EventWriteScope
import cloud.flashcat.android.api.feature.FeatureScope
import cloud.flashcat.android.api.storage.DataWriter
import cloud.flashcat.android.core.internal.net.FirstPartyHostHeaderTypeResolver
import cloud.flashcat.android.rum.RumSessionListener
import cloud.flashcat.android.rum.RumSessionType
import cloud.flashcat.android.rum.internal.FeaturesContextResolver
import cloud.flashcat.android.rum.internal.domain.InfoProvider
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
import cloud.flashcat.android.rum.model.ViewEvent
import cloud.flashcat.android.rum.utils.config.GlobalRumMonitorTestConfiguration
import cloud.flashcat.android.rum.utils.forge.Configurator
import cloud.flashcat.tools.unit.annotations.TestConfigurationsProvider
import cloud.flashcat.tools.unit.extensions.TestConfigurationExtension
import cloud.flashcat.tools.unit.extensions.config.TestConfiguration
import cloud.flashcat.tools.unit.forge.exhaustiveAttributes
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
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
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumApplicationScopeAttributePropagationTest {

    lateinit var testedScope: RumApplicationScope

    @Mock
    lateinit var mockParentScope: RumScope

    @Mock
    lateinit var mockWriter: DataWriter<Any>

    @Mock
    lateinit var mockResolver: FirstPartyHostHeaderTypeResolver

    @Mock
    lateinit var mockSessionListener: RumSessionListener

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
    lateinit var mockRumFeatureScope: FeatureScope

    @Mock
    lateinit var mockEventWriteScope: EventWriteScope

    @Mock
    lateinit var mockFeaturesContextResolver: FeaturesContextResolver

    @Mock
    lateinit var mockSessionEndedMetricDispatcher: SessionMetricDispatcher

    @Mock
    lateinit var mockInitialResourceIdentifier: InitialResourceIdentifier

    @Mock
    lateinit var mockAccessibilitySnapshotManager: AccessibilitySnapshotManager

    @Mock
    lateinit var mockBatteryInfoProvider: InfoProvider<BatteryInfo>

    @Mock
    lateinit var mockDisplayInfoProvider: InfoProvider<DisplayInfo>

    @Mock
    lateinit var mockRumAppStartupTelemetryReporter: RumAppStartupTelemetryReporter

    @Mock
    lateinit var mockSlowFramesListener: SlowFramesListener

    lateinit var fakeEventTime: Time

    lateinit var fakeEvent: RumRawEvent

    @Forgery
    lateinit var fakeTimeInfoAtScopeStart: TimeInfo

    @Forgery
    lateinit var fakeNetworkInfoAtScopeStart: NetworkInfo

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    lateinit var fakeGlobalAttributes: Map<String, Any?>

    @BoolForgery
    var fakeHasReplay: Boolean = false

    @FloatForgery(min = 0f, max = 100f)
    var fakeSampleRate: Float = 0f

    @BoolForgery
    var fakeBackgroundTrackingEnabled: Boolean = false

    @BoolForgery
    var fakeTrackFrustrations: Boolean = true

    @StringForgery
    lateinit var fakeApplicationId: String

    private var fakeRumSessionType: RumSessionType? = null

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeGlobalAttributes = forge.exhaustiveAttributes()

        fakeDatadogContext = fakeDatadogContext.copy(
            source = forge.aValueFrom(ViewEvent.ViewEventSource::class.java).toJson().asString
        )

        val fakeOffset = -forge.aLong(1000, 50000)
        val fakeTimestamp = System.currentTimeMillis() + fakeOffset
        val fakeNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(fakeOffset)
        val maxLimit = max(Long.MAX_VALUE - fakeTimestamp, Long.MAX_VALUE)
        val minLimit = min(-fakeTimestamp, maxLimit)

        fakeDatadogContext = fakeDatadogContext.copy(
            time = fakeTimeInfoAtScopeStart.copy(
                serverTimeOffsetMs = forge.aLong(min = minLimit, max = maxLimit)
            )
        )
        fakeEventTime = Time(fakeTimestamp, fakeNanos)

        whenever(mockRumFeatureScope.withWriteContext(any(), any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventWriteScope) -> Unit>(1)
            callback.invoke(fakeDatadogContext, mockEventWriteScope)
        }

        whenever(rumMonitor.mockSdkCore.internalLogger) doReturn mock()
        fakeRumSessionType = forge.aNullable { aValueFrom(RumSessionType::class.java) }
        testedScope = RumApplicationScope(
            applicationId = fakeApplicationId,
            sdkCore = rumMonitor.mockSdkCore,
            sampleRate = fakeSampleRate,
            backgroundTrackingEnabled = fakeBackgroundTrackingEnabled,
            trackFrustrations = fakeTrackFrustrations,
            firstPartyHostHeaderTypeResolver = mockResolver,
            cpuVitalMonitor = mockCpuVitalMonitor,
            memoryVitalMonitor = mockMemoryVitalMonitor,
            frameRateVitalMonitor = mockFrameRateVitalMonitor,
            sessionEndedMetricDispatcher = mockSessionEndedMetricDispatcher,
            sessionListener = mockSessionListener,
            initialResourceIdentifier = mockInitialResourceIdentifier,
            lastInteractionIdentifier = mockLastInteractionIdentifier,
            slowFramesListener = mockSlowFramesListener,
            rumSessionTypeOverride = fakeRumSessionType,
            accessibilitySnapshotManager = mockAccessibilitySnapshotManager,
            batteryInfoProvider = mockBatteryInfoProvider,
            displayInfoProvider = mockDisplayInfoProvider,
            rumAppStartupTelemetryReporter = mockRumAppStartupTelemetryReporter
        )
    }

    // region Propagate parent attributes

    @Test
    fun `M return global attributes W getCustomAttributes()`() {
        // Given
        whenever(rumMonitor.mockInstance.getAttributes()) doReturn fakeGlobalAttributes

        // When
        val customAttributes = testedScope.getCustomAttributes()

        // Then
        assertThat(customAttributes)
            .containsExactlyInAnyOrderEntriesOf(fakeGlobalAttributes)
    }

    // endregion

    companion object {

        val rumMonitor = GlobalRumMonitorTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(rumMonitor)
        }
    }
}
