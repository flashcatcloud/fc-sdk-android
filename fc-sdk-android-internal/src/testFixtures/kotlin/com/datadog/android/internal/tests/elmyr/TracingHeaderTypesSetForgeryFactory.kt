/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.internal.tests.elmyr

import com.flashcat.rum.internal.telemetry.TracingHeaderType
import com.flashcat.rum.internal.telemetry.TracingHeaderTypesSet
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

class TracingHeaderTypesSetForgeryFactory : ForgeryFactory<TracingHeaderTypesSet> {
    override fun getForgery(forge: Forge): TracingHeaderTypesSet {
        return TracingHeaderTypesSet(
            types = forge.aList {
                aValueFrom(TracingHeaderType::class.java)
            }.toSet()
        )
    }
}
