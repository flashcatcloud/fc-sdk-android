/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.core.internal

import com.flashcat.rum.FlashcatSite
import com.flashcat.rum.api.context.FlashcatContext
import com.flashcat.rum.api.context.DeviceInfo
import com.flashcat.rum.api.context.DeviceType
import com.flashcat.rum.api.context.LocaleInfo
import com.flashcat.rum.api.context.NetworkInfo
import com.flashcat.rum.api.context.ProcessInfo
import com.flashcat.rum.api.context.TimeInfo
import com.flashcat.rum.api.context.UserInfo
import com.flashcat.rum.privacy.TrackingConsent

internal class NoOpContextProvider : ContextProvider {
    // TODO RUM-3784 this one is quite ugly. Should return type be nullable?
    override fun getContext(withFeatureContexts: Set<String>) = FlashcatContext(
        site = FlashcatSite.US1,
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
