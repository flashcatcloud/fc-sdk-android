/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.sdk.integration.sessionreplay.sensitivefields

import cloud.flashcat.android.privacy.TrackingConsent
import cloud.flashcat.android.sdk.integration.sessionreplay.BaseSessionReplayTest
import cloud.flashcat.android.sdk.integration.sessionreplay.SessionReplaySensitiveFieldsActivity
import cloud.flashcat.android.sdk.rules.SessionReplayTestRule
import cloud.flashcat.android.sdk.utils.SR_PRIVACY_LEVEL
import cloud.flashcat.android.sessionreplay.SessionReplayPrivacy
import org.junit.Rule
import org.junit.Test

internal class SrSensitiveFieldsMaskUserInputTest : BaseSessionReplayTest<SessionReplaySensitiveFieldsActivity>() {

    @get:Rule
    val rule = SessionReplayTestRule(
        SessionReplaySensitiveFieldsActivity::class.java,
        trackingConsent = TrackingConsent.GRANTED,
        keepRequests = true,
        intentExtras = mapOf(SR_PRIVACY_LEVEL to SessionReplayPrivacy.MASK_USER_INPUT)
    )

    @Test
    fun assessRecordedScreenPayload() {
        runInstrumentationScenario()
        assessSrPayload(EXPECTED_PAYLOAD_FILE_NAME, rule)
    }

    companion object {
        const val EXPECTED_PAYLOAD_FILE_NAME = "sr_sensitive_fields_mask_user_input_payload.json"
    }
}
