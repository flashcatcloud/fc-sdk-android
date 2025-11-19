/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.internal.tests.elmyr

import com.flashcat.rum.internal.telemetry.InternalTelemetryEvent
import com.datadog.tools.unit.forge.aThrowable
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

class InternalTelemetryErrorLogForgeryFactory : ForgeryFactory<InternalTelemetryEvent.Log.Error> {

    override fun getForgery(forge: Forge): InternalTelemetryEvent.Log.Error {
        return InternalTelemetryEvent.Log.Error(
            message = forge.aString(),
            additionalProperties = forge.aMap { aString() to aString() },
            error = forge.aNullable { aThrowable() },
            stacktrace = forge.aNullable { aString() },
            kind = forge.aNullable { aString() }
        )
    }
}
