/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.tests.elmyr

import com.flashcat.rum.api.context.DeviceInfo
import com.flashcat.rum.api.context.DeviceType
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

class DeviceInfoForgeryFactory : ForgeryFactory<DeviceInfo> {
    override fun getForgery(forge: Forge): DeviceInfo {
        return DeviceInfo(
            deviceName = forge.anAlphabeticalString(),
            deviceBrand = forge.anAlphabeticalString(),
            deviceModel = forge.anAlphabeticalString(),
            deviceType = forge.aValueFrom(DeviceType::class.java),
            deviceBuildId = forge.anAlphaNumericalString(),
            osName = forge.aString(),
            osVersion = forge.aString(),
            osMajorVersion = forge.aString(),
            architecture = forge.aString(),
            numberOfDisplays = forge.aNullable { forge.anInt() },
            localeInfo = forge.getForgery()
        )
    }
}
