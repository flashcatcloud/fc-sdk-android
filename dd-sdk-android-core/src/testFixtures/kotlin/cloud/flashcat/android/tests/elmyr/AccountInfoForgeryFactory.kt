/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.tests.elmyr

import cloud.flashcat.android.api.context.AccountInfo
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

class AccountInfoForgeryFactory : ForgeryFactory<AccountInfo> {

    override fun getForgery(forge: Forge): AccountInfo {
        return AccountInfo(
            id = forge.anHexadecimalString(),
            name = forge.aNullable { forge.aStringMatching("[A-Z][a-z]+ [A-Z]\\. [A-Z][a-z]+") },
            extraInfo = forge.exhaustiveAttributes(
                excludedKeys = setOf(
                    "id",
                    "name"
                )
            )
        )
    }
}
