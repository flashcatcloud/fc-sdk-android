/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.rum.utils.forge

import cloud.flashcat.android.rum.internal.metric.NoValueReason
import cloud.flashcat.android.rum.internal.metric.ViewInitializationMetricsConfig
import cloud.flashcat.android.rum.internal.metric.ViewInitializationMetricsState
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class TelemetryViewInitializationMetricsStateForgeryFactory : ForgeryFactory<ViewInitializationMetricsState> {
    override fun getForgery(forge: Forge): ViewInitializationMetricsState {
        val initializationTime = forge.aNullable { aLong(min = 0L) }
        return ViewInitializationMetricsState(
            initializationTime = initializationTime,
            config = forge.aValueFrom(ViewInitializationMetricsConfig::class.java),
            noValueReason = forge.anElementFrom(
                forge.aValueFrom(NoValueReason.TimeToNetworkSettle::class.java),
                forge.aValueFrom(NoValueReason.InteractionToNextView::class.java)
            ).takeIf { initializationTime == null }
        )
    }
}
