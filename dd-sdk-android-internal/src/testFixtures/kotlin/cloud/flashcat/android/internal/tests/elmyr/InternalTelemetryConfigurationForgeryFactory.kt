/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.internal.tests.elmyr

import cloud.flashcat.android.internal.telemetry.InternalTelemetryEvent
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

class InternalTelemetryConfigurationForgeryFactory : ForgeryFactory<InternalTelemetryEvent.Configuration> {

    override fun getForgery(forge: Forge): InternalTelemetryEvent.Configuration {
        return InternalTelemetryEvent.Configuration(
            trackErrors = forge.aBool(),
            batchSize = forge.aLong(),
            batchProcessingLevel = forge.anInt(),
            batchUploadFrequency = forge.aLong(),
            useProxy = forge.aBool(),
            useLocalEncryption = forge.aBool()
        )
    }
}
