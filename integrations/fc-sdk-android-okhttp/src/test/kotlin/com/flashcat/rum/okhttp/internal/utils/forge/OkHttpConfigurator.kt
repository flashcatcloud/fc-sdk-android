/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.okhttp.internal.utils.forge

import com.flashcat.rum.internal.tests.elmyr.TracingHeaderTypesSetForgeryFactory
import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.Forge

internal class OkHttpConfigurator : BaseConfigurator() {

    override fun configure(forge: Forge) {
        super.configure(forge)

        // custom factories
        forge.addFactory(TraceContextFactory())
        forge.addFactory(TracingHeaderTypesSetForgeryFactory())
    }
}
