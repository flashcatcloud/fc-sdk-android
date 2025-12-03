/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.utils.forge

import cloud.flashcat.android.tests.elmyr.useCoreFactories
import cloud.flashcat.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.Forge

internal class Configurator : BaseConfigurator() {

    override fun configure(forge: Forge) {
        super.configure(forge)

        // Core
        forge.useCoreFactories()

        // APM
        forge.addFactory(SpanEventForgeryFactory())
        forge.addFactory(TraceConfigurationForgeryFactory())
        forge.addFactory(CoreDDSpanForgeryFactory())
        forge.addFactory(AgentSpanLinkForgeryFactory())
        forge.addFactory(DatadogSpanLinkForgery())
        forge.addFactory(DDTraceIdForgeryFactory())
        forge.addFactory(DatadogSpanForgeryFactory())
        forge.addFactory(DatadogTraceIdForgeryFactory())
        forge.addFactory(DatadogSpanContextForgeryFactory())
    }
}
