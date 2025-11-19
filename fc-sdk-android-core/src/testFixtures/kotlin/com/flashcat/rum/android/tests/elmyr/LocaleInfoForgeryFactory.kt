/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.tests.elmyr

import com.flashcat.rum.api.context.LocaleInfo
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

class LocaleInfoForgeryFactory : ForgeryFactory<LocaleInfo> {

    override fun getForgery(forge: Forge): LocaleInfo {
        return LocaleInfo(
            locales = forge.aList { forge.aString() },
            currentLocale = forge.aString(),
            timeZone = forge.aString()
        )
    }
}
