/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sdk.integration.sessionreplay.images

import com.flashcat.rum.privacy.TrackingConsent
import com.flashcat.rum.sdk.integration.sessionreplay.BaseSessionReplayTest
import com.flashcat.rum.sdk.integration.sessionreplay.SessionReplayImagesActivity
import com.flashcat.rum.sdk.rules.SessionReplayTestRule
import com.flashcat.rum.sdk.utils.SR_PRIVACY_LEVEL
import com.flashcat.rum.sessionreplay.SessionReplayPrivacy
import org.junit.Rule
import org.junit.Test

internal class SrImagesAllowTest :
    BaseSessionReplayTest<SessionReplayImagesActivity>() {

    @get:Rule
    val rule = SessionReplayTestRule(
        SessionReplayImagesActivity::class.java,
        trackingConsent = TrackingConsent.GRANTED,
        keepRequests = true,
        intentExtras = mapOf(SR_PRIVACY_LEVEL to SessionReplayPrivacy.ALLOW)
    )

    @Test
    fun assessRecordedScreenPayload() {
        runInstrumentationScenario()
        assessSrPayload(EXPECTED_PAYLOAD_FILE_NAME, rule)
    }
    companion object {
        const val EXPECTED_PAYLOAD_FILE_NAME = "sr_images_allow_payload.json"
    }
}
