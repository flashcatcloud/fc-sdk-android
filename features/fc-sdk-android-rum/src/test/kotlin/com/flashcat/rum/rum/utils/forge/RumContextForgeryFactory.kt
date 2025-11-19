/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.rum.utils.forge

import com.flashcat.rum.rum.internal.domain.RumContext
import com.flashcat.rum.rum.internal.domain.scope.RumSessionScope
import com.flashcat.rum.rum.internal.domain.scope.RumViewType
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import java.util.UUID

internal class RumContextForgeryFactory : ForgeryFactory<RumContext> {
    override fun getForgery(forge: Forge): RumContext {
        return RumContext(
            applicationId = forge.getForgery<UUID>().toString(),
            sessionId = forge.getForgery<UUID>().toString(),
            isSessionActive = forge.aBool(),
            viewId = forge.aNullable { getForgery<UUID>().toString() },
            viewName = forge.aNullable { forge.anAlphaNumericalString() },
            viewUrl = forge.aStringMatching("http(s?)://[a-z]+\\.com/[a-z]+"),
            actionId = forge.aNullable { getForgery<UUID>().toString() },
            sessionState = forge.aValueFrom(RumSessionScope.State::class.java),
            sessionStartReason = forge.aValueFrom(RumSessionScope.StartReason::class.java),
            viewType = forge.aValueFrom(RumViewType::class.java),
            syntheticsTestId = forge.aNullable { forge.anAlphaNumericalString() },
            syntheticsResultId = forge.aNullable { forge.anAlphaNumericalString() }
        )
    }
}
