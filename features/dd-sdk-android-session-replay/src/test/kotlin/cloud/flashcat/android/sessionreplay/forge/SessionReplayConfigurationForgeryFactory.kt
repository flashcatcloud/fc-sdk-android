/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.sessionreplay.forge

import cloud.flashcat.android.sessionreplay.ImagePrivacy
import cloud.flashcat.android.sessionreplay.NoOpSessionReplayInternalCallback
import cloud.flashcat.android.sessionreplay.SessionReplayConfiguration
import cloud.flashcat.android.sessionreplay.SessionReplayPrivacy
import cloud.flashcat.android.sessionreplay.SystemRequirementsConfiguration
import cloud.flashcat.android.sessionreplay.TextAndInputPrivacy
import cloud.flashcat.android.sessionreplay.TouchPrivacy
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
