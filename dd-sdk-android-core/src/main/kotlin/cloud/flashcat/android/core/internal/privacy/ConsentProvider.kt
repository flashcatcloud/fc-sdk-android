/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.core.internal.privacy

import cloud.flashcat.android.privacy.TrackingConsent
import cloud.flashcat.android.privacy.TrackingConsentProviderCallback
import cloud.flashcat.tools.annotation.NoOpImplementation

@NoOpImplementation
internal interface ConsentProvider {

    fun getConsent(): TrackingConsent

    fun setConsent(consent: TrackingConsent)

    fun registerCallback(callback: TrackingConsentProviderCallback)

    fun unregisterCallback(callback: TrackingConsentProviderCallback)

    fun unregisterAllCallbacks()
}
