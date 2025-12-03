/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.flags.utils.forge

import cloud.flashcat.android.flags.internal.model.PrecomputedFlag
import cloud.flashcat.android.flags.internal.model.VariationType
import cloud.flashcat.android.flags.model.ResolutionReason
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import org.json.JSONObject

internal class PrecomputedFlagForgeryFactory : ForgeryFactory<PrecomputedFlag> {
    override fun getForgery(forge: Forge): PrecomputedFlag {
        val variationTypeEnum = forge.anElementFrom(*VariationType.values())
        val variationType = variationTypeEnum.value

        val variationValue = when (variationTypeEnum) {
            VariationType.BOOLEAN -> forge.aBool().toString()
            VariationType.STRING -> forge.anAlphabeticalString()
            VariationType.INTEGER -> forge.anInt().toString()
            VariationType.NUMBER -> forge.aDouble().toString()
            VariationType.FLOAT -> forge.aFloat().toString()
            VariationType.OBJECT -> JSONObject().put(
                "key",
                forge.anAlphabeticalString()
            ).toString()
        }

        val reasonEnum = forge.aValueFrom(ResolutionReason::class.java)
        val reason = reasonEnum.name

        return PrecomputedFlag(
            variationType = variationType,
            variationValue = variationValue,
            doLog = forge.aBool(),
            allocationKey = forge.anAlphabeticalString(),
            variationKey = forge.anAlphabeticalString(),
            extraLogging = forge.aNullable {
                JSONObject().apply {
                    repeat(forge.anInt(0, 5)) {
                        put(forge.anAlphabeticalString(), forge.anAlphabeticalString())
                    }
                }
            } ?: JSONObject(),
            reason = reason
        )
    }
}
