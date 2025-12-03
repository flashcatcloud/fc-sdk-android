/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.sdk.integration.sessionreplay

import cloud.flashcat.android.privacy.TrackingConsent
import cloud.flashcat.android.sdk.integration.RuntimeConfig
import cloud.flashcat.android.sdk.rules.SessionReplayTestRule
import cloud.flashcat.android.sdk.utils.SR_SAMPLE_RATE
import cloud.flashcat.tools.unit.ConditionWatcher
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
