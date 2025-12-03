/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.tests.elmyr

import cloud.flashcat.android.FlashcatSite
import cloud.flashcat.android.api.context.DatadogContext
import cloud.flashcat.android.privacy.TrackingConsent
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import java.util.Locale
import java.util.UUID

class DatadogContextForgeryFactory : ForgeryFactory<DatadogContext> {

    override fun getForgery(forge: Forge): DatadogContext {
        return DatadogContext(
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
