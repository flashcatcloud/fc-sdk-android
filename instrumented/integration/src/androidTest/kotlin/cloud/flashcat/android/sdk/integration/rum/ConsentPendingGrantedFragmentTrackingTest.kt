/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.sdk.integration.rum

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import cloud.flashcat.android.Datadog
import cloud.flashcat.android.privacy.TrackingConsent
import cloud.flashcat.android.sdk.integration.RuntimeConfig
import cloud.flashcat.android.sdk.rules.RumMockServerActivityTestRule
import cloud.flashcat.tools.unit.ConditionWatcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
internal class ConsentPendingGrantedFragmentTrackingTest : FragmentTrackingTest() {

    @get:Rule
    val mockServerRule = RumMockServerActivityTestRule(
        FragmentTrackingPlaygroundActivity::class.java,
        keepRequests = true,
        trackingConsent = TrackingConsent.PENDING
    )

    @Test
    fun verifyViewEventsOnSwipe() {
        val expectedEvents = runInstrumentationScenario(mockServerRule)

        // update the tracking consent
        Datadog.setTrackingConsent(TrackingConsent.GRANTED)

        // Wait to make sure all batches are consumed
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        ConditionWatcher {
            verifyExpectedEvents(
                mockServerRule.getRequests(RuntimeConfig.rumEndpointUrl),
                expectedEvents
            )
            true
        }.doWait(timeoutMs = FINAL_WAIT_MS)
    }
}
