/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sdk.integration.rum

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.flashcat.rum.Flashcat
import com.flashcat.rum.privacy.TrackingConsent
import com.flashcat.rum.sdk.integration.RuntimeConfig
import com.flashcat.rum.sdk.rules.GesturesTrackingActivityTestRule
import com.datadog.tools.unit.ConditionWatcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
internal class ConsentPendingGrantedGesturesTrackingTest : GesturesTrackingTest() {

    @get:Rule
    val mockServerRule = GesturesTrackingActivityTestRule(
        GesturesTrackingPlaygroundActivity::class.java,
        keepRequests = true,
        trackingConsent = TrackingConsent.PENDING
    )

    @Test
    fun verifyTrackedGestures() {
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
