/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.tests.elmyr

import com.flashcat.rum.FlashcatSite
import com.flashcat.rum.api.context.FlashcatContext
import com.flashcat.rum.privacy.TrackingConsent
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import java.util.Locale
import java.util.UUID

internal class FlashcatContextForgeryFactory : ForgeryFactory<FlashcatContext> {

    override fun getForgery(forge: Forge): FlashcatContext {
        return FlashcatContext(
            site = forge.aValueFrom(FlashcatSite::class.java),
            clientToken = forge.anHexadecimalString().lowercase(Locale.US),
            service = forge.anAlphabeticalString(),
            version = forge.aStringMatching("[0-9](\\.[0-9]{1,3}){2,3}"),
            variant = forge.anAlphabeticalString(),
            env = forge.anAlphabeticalString().lowercase(Locale.US),
            source = forge.anAlphabeticalString(),
            sdkVersion = forge.aStringMatching("[0-9](\\.[0-9]{1,2}){1,3}"),
            time = forge.getForgery(),
            processInfo = forge.getForgery(),
            networkInfo = forge.getForgery(),
            deviceInfo = forge.getForgery(),
            userInfo = forge.getForgery(),
            accountInfo = forge.getForgery(),
            trackingConsent = forge.aValueFrom(TrackingConsent::class.java),
            appBuildId = forge.aNullable { getForgery<UUID>().toString() },
            // building nested maps with default size slows down tests quite a lot, so will use
            // an explicit small size
            featuresContext = forge.aMap(size = 2) {
                forge.anAlphabeticalString() to forge.exhaustiveAttributes()
            }
        )
    }
}
