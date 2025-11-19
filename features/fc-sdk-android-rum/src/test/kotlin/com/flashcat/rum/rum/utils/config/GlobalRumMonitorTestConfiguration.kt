/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.rum.utils.config

import com.flashcat.rum.core.InternalSdkCore
import com.flashcat.rum.rum.GlobalRumMonitor
import com.flashcat.rum.rum.RumMonitor
import com.flashcat.rum.rum._RumInternalProxy
import com.flashcat.rum.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.tools.unit.extensions.config.MockTestConfiguration
import fr.xgouchet.elmyr.Forge
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@Suppress("TestFunctionName")
internal abstract class InternalAdvancedRumMonitor : AdvancedRumMonitor {
    override fun _getInternal(): _RumInternalProxy? {
        return null
    }
}

internal class GlobalRumMonitorTestConfiguration :
    MockTestConfiguration<RumMonitor>(InternalAdvancedRumMonitor::class.java) {

    lateinit var mockSdkCore: InternalSdkCore

    override fun setUp(forge: Forge) {
        super.setUp(forge)
        mockSdkCore = mock()

        (mockInstance as? InternalAdvancedRumMonitor)?.let {
            whenever(it._getInternal()).thenReturn(_RumInternalProxy(mockInstance as AdvancedRumMonitor))
        }

        GlobalRumMonitor.registerIfAbsent(mockInstance, mockSdkCore)
    }

    override fun tearDown(forge: Forge) {
        GlobalRumMonitor.clear()
        super.tearDown(forge)
    }
}
