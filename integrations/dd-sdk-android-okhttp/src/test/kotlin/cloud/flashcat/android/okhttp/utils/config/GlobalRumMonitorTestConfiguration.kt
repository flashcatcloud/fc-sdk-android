/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.okhttp.utils.config

import cloud.flashcat.android.core.InternalSdkCore
import cloud.flashcat.android.rum.GlobalRumMonitor
import cloud.flashcat.android.rum.RumMonitor
import cloud.flashcat.android.rum.internal.monitor.AdvancedNetworkRumMonitor
import cloud.flashcat.tools.unit.extensions.config.MockTestConfiguration
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
