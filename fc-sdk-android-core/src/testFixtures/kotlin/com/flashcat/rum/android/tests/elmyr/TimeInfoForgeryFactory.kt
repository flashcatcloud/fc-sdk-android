/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.tests.elmyr

import com.flashcat.rum.api.context.TimeInfo
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

class TimeInfoForgeryFactory : ForgeryFactory<TimeInfo> {
    override fun getForgery(forge: Forge): TimeInfo {
        return TimeInfo(
            deviceTimeNs = forge.aLong(min = 0),
            serverTimeNs = forge.aLong(min = 0),
            serverTimeOffsetNs = forge.aLong(),
            serverTimeOffsetMs = forge.aLong()
        )
    }
}
