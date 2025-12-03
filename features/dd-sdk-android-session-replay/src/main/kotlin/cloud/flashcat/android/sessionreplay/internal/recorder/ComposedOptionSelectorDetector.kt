/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.sessionreplay.internal.recorder

import android.view.ViewGroup
import cloud.flashcat.android.sessionreplay.recorder.OptionSelectorDetector

internal class ComposedOptionSelectorDetector(private val detectors: List<OptionSelectorDetector>) :
    OptionSelectorDetector {
    override fun isOptionSelector(view: ViewGroup): Boolean {
        return detectors.any { it.isOptionSelector(view) }
    }
}
