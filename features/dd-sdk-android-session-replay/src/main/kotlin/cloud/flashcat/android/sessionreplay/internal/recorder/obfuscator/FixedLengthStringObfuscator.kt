/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.sessionreplay.internal.recorder.obfuscator

import cloud.flashcat.android.sessionreplay.recorder.mapper.TextViewMapper

internal class FixedLengthStringObfuscator : StringObfuscator {
    override fun obfuscate(stringValue: String): String {
        return TextViewMapper.FIXED_INPUT_MASK
    }
}
