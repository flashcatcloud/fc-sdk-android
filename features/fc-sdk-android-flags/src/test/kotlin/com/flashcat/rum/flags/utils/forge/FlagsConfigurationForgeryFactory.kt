/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.flags.utils.forge

import com.flashcat.rum.flags.FlagsConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class FlagsConfigurationForgeryFactory : ForgeryFactory<FlagsConfiguration> {
    override fun getForgery(forge: Forge): FlagsConfiguration = FlagsConfiguration.Builder()
        .trackExposures(forge.aBool())
        .build()
}
