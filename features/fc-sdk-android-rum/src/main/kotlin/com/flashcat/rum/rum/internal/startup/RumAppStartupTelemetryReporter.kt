/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.rum.internal.startup

import com.flashcat.rum.core.InternalSdkCore
import com.flashcat.rum.rum.DdRumContentProvider

internal interface RumAppStartupTelemetryReporter {
    fun reportTTID(info: RumTTIDInfo, indexInSession: Int)

    companion object {
        fun create(sdkCore: InternalSdkCore): RumAppStartupTelemetryReporter {
            return RumAppStartupTelemetryReporterImpl(
                internalLogger = sdkCore.internalLogger,
                appStartupTimeNs = sdkCore.appStartTimeNs,
                contentProviderCreationTimeNs = DdRumContentProvider.createTimeNs,
                processStartImportance = DdRumContentProvider.processImportance
            )
        }
    }
}
