/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.flags.utils.forge

import cloud.flashcat.android.tests.elmyr.useCoreFactories
import cloud.flashcat.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.Forge

internal class ForgeConfigurator : BaseConfigurator() {

    override fun configure(forge: Forge) {
        super.configure(forge)

        // so that we can forge DatadogContext
        forge.useCoreFactories()

        forge.addFactory(FlagsConfigurationForgeryFactory())
        forge.addFactory(PrecomputedFlagForgeryFactory())
    }
}
