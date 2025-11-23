/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package com.datadog.android.rum.internal.startup

import android.app.Application
import com.flashcat.android.core.InternalSdkCore
import com.flashcat.android.core.internal.system.BuildSdkVersionProvider
import com.datadog.android.rum.DdRumContentProvider

internal interface RumAppStartupDetector {
    interface Listener {
        fun onAppStartupDetected(scenario: RumStartupScenario)
    }

    fun destroy()

    companion object {
        fun create(
            application: Application,
            sdkCore: InternalSdkCore,
            listener: Listener
        ): RumAppStartupDetector {
            return RumAppStartupDetectorImpl(
                application = application,
                buildSdkVersionProvider = BuildSdkVersionProvider.DEFAULT,
                appStartupTimeProviderNs = { sdkCore.appStartTimeNs },
                processImportanceProvider = { DdRumContentProvider.processImportance },
                timeProviderNs = { System.nanoTime() },
                listener
            )
        }
    }
}
