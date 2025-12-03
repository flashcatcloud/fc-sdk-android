/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.utils.forge

import cloud.flashcat.android.log.LogsConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import org.mockito.kotlin.mock

class LogsConfigurationForgeryFactory : ForgeryFactory<LogsConfiguration> {
    override fun getForgery(forge: Forge): LogsConfiguration {
        return LogsConfiguration(
            customEndpointUrl = forge.aNullable {
                aStringMatching("https://[a-z]+\\.com")
            },
            eventMapper = mock()
        )
    }
}
