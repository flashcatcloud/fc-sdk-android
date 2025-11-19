/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sessionreplay.forge

import com.flashcat.rum.sessionreplay.ImagePrivacy
import com.flashcat.rum.sessionreplay.NoOpSessionReplayInternalCallback
import com.flashcat.rum.sessionreplay.SessionReplayConfiguration
import com.flashcat.rum.sessionreplay.SessionReplayPrivacy
import com.flashcat.rum.sessionreplay.SystemRequirementsConfiguration
import com.flashcat.rum.sessionreplay.TextAndInputPrivacy
import com.flashcat.rum.sessionreplay.TouchPrivacy
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import org.mockito.kotlin.mock

class SessionReplayConfigurationForgeryFactory : ForgeryFactory<SessionReplayConfiguration> {
    override fun getForgery(forge: Forge): SessionReplayConfiguration {
        return SessionReplayConfiguration(
            customEndpointUrl = forge.aNullable { aStringMatching("https://[a-z]+\\.com") },
            privacy = forge.aValueFrom(SessionReplayPrivacy::class.java),
            textAndInputPrivacy = forge.aValueFrom(TextAndInputPrivacy::class.java),
            imagePrivacy = forge.aValueFrom(ImagePrivacy::class.java),
            touchPrivacy = forge.aValueFrom(TouchPrivacy::class.java),
            customMappers = forge.aList { mock() },
            customOptionSelectorDetectors = forge.aList { mock() },
            customDrawableMappers = forge.aList { mock() },
            startRecordingImmediately = forge.aBool(),
            sampleRate = forge.aFloat(min = 0f, max = 100f),
            dynamicOptimizationEnabled = forge.aBool(),
            internalCallback = NoOpSessionReplayInternalCallback(),
            systemRequirementsConfiguration = SystemRequirementsConfiguration.Builder()
                .setMinRAMSizeMb(forge.aSmallInt())
                .setMinCPUCoreNumber(forge.aSmallInt())
                .build()
        )
    }
}
