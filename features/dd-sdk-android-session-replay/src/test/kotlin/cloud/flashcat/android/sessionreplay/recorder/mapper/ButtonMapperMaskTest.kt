/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.sessionreplay.recorder.mapper

import cloud.flashcat.android.sessionreplay.TextAndInputPrivacy
import cloud.flashcat.android.sessionreplay.forge.ForgeConfigurator
import cloud.flashcat.android.sessionreplay.internal.recorder.mapper.ButtonMapperTest
import cloud.flashcat.android.sessionreplay.internal.recorder.obfuscator.StringObfuscator
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class ButtonMapperMaskTest : ButtonMapperTest() {

    override fun expectedPrivacyCompliantText(text: String): String {
        return if (fakeMappingContext.hasOptionSelectorParent) {
            TextViewMapper.FIXED_INPUT_MASK
        } else {
            StringObfuscator.getStringObfuscator().obfuscate(text)
        }
    }

    override fun privacyOption(): TextAndInputPrivacy = TextAndInputPrivacy.MASK_ALL
}
