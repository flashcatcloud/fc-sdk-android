/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sdk.integration.sessionreplay

import com.flashcat.rum.privacy.TrackingConsent
import com.flashcat.rum.sdk.integration.RuntimeConfig
import com.flashcat.rum.sdk.rules.SessionReplayTestRule
import com.flashcat.rum.sdk.utils.SR_SAMPLE_RATE
import com.flashcat.tools.unit.ConditionWatcher
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test

internal class SampledOutSrTest : BaseSessionReplayTest<SessionReplayPlaygroundActivity>() {

    @get:Rule
    val rule = SessionReplayTestRule(
        SessionReplayPlaygroundActivity::class.java,
        trackingConsent = TrackingConsent.GRANTED,
        keepRequests = true,
        intentExtras = mapOf(
            SR_SAMPLE_RATE to 0f
        )
    )

    @Test
    fun verifySessionFirstSnapshot() {
        runInstrumentationScenario()
        ConditionWatcher {
            assertThat(rule.getRequests(RuntimeConfig.sessionReplayEndpointUrl)).isEmpty()
            true
        }.doWait(timeoutMs = INITIAL_WAIT_MS)
    }
}
