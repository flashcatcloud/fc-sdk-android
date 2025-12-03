/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.core.internal

import cloud.flashcat.android.FlashcatSite
import cloud.flashcat.android.api.context.DatadogContext
import cloud.flashcat.android.api.context.DeviceInfo
import cloud.flashcat.android.api.context.DeviceType
import cloud.flashcat.android.api.context.LocaleInfo
import cloud.flashcat.android.api.context.NetworkInfo
import cloud.flashcat.android.api.context.ProcessInfo
import cloud.flashcat.android.api.context.TimeInfo
import cloud.flashcat.android.api.context.UserInfo
import cloud.flashcat.android.privacy.TrackingConsent

internal class NoOpContextProvider : ContextProvider {
    // TODO RUM-3784 this one is quite ugly. Should return type be nullable?
    override fun getContext(withFeatureContexts: Set<String>) = DatadogContext(
        site = FlashcatSite.CN,
        clientToken = "",
        service = "",
        env = "",
        version = "",
        variant = "",
        source = "",
        sdkVersion = "",
        time = TimeInfo(
            deviceTimeNs = 0L,
            serverTimeNs = 0L,
            serverTimeOffsetMs = 0L,
            serverTimeOffsetNs = 0L
        ),
        processInfo = ProcessInfo(isMainProcess = true),
        networkInfo = NetworkInfo(
            connectivity = NetworkInfo.Connectivity.NETWORK_OTHER,
            carrierName = null,
            carrierId = null,
            upKbps = null,
            downKbps = null,
            strength = null,
            cellularTechnology = null
        ),
        deviceInfo = DeviceInfo(
            deviceName = "",
            deviceBrand = "",
            deviceModel = "",
            deviceType = DeviceType.OTHER,
            deviceBuildId = "",
            osName = "",
            osMajorVersion = "",
            osVersion = "",
            architecture = "",
            numberOfDisplays = null,
            localeInfo = LocaleInfo(
                locales = emptyList(),
                currentLocale = "",
                timeZone = ""
            )
        ),
        userInfo = UserInfo(null, null, null, null, emptyMap()),
        accountInfo = null,
        trackingConsent = TrackingConsent.NOT_GRANTED,
        appBuildId = null,
        featuresContext = emptyMap()
    )
}
