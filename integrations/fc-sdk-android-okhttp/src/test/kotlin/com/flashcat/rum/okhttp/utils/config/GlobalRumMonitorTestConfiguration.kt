/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.okhttp.utils.config

import com.flashcat.rum.core.InternalSdkCore
import com.flashcat.rum.rum.GlobalRumMonitor
import com.flashcat.rum.rum.RumMonitor
import com.flashcat.rum.rum.internal.monitor.AdvancedNetworkRumMonitor
import com.datadog.tools.unit.extensions.config.MockTestConfiguration
import fr.xgouchet.elmyr.Forge
import org.mockito.kotlin.mock
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.isAccessible

// TODO RUMM-2949 Share forgeries/test configurations between modules
internal class GlobalRumMonitorTestConfiguration(
    private val datadogSingletonTestConfiguration: DatadogSingletonTestConfiguration? = null
) : MockTestConfiguration<FakeRumMonitor>(FakeRumMonitor::class.java) {

    lateinit var mockSdkCore: InternalSdkCore

    override fun setUp(forge: Forge) {
        super.setUp(forge)
        mockSdkCore = datadogSingletonTestConfiguration?.mockInstance ?: mock()
        GlobalRumMonitor::class.declaredFunctions.first { it.name == "registerIfAbsent" }.apply {
            isAccessible = true
            call(GlobalRumMonitor::class.objectInstance, mockInstance, mockSdkCore)
        }
    }

    override fun tearDown(forge: Forge) {
        GlobalRumMonitor::class.java.getDeclaredMethod("reset").apply {
            isAccessible = true
            invoke(null)
        }
        super.tearDown(forge)
    }
}

internal interface FakeRumMonitor : RumMonitor, AdvancedNetworkRumMonitor
