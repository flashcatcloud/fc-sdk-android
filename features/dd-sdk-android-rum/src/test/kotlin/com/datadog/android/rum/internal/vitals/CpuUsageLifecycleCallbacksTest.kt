/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.vitals

import android.app.Activity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

internal class CpuUsageLifecycleCallbacksTest {
    @Test
    fun `excludes paused periods and supports multiple resumed activities`() {
        var active = false
        val callback = CpuUsageLifecycleCallbacks { active = it }
        val first = mock<Activity>()
        val second = mock<Activity>()
        callback.onActivityStarted(first)
        assertThat(active).isFalse()
        callback.onActivityResumed(first)
        callback.onActivityResumed(second)
        callback.onActivityPaused(first)
        assertThat(active).isTrue()
        callback.onActivityPaused(second)
        assertThat(active).isFalse()
        callback.onActivityResumed(first)
        assertThat(active).isTrue()
    }

    @Test
    fun `handles initialization after an activity was already resumed`() {
        var active = true
        val callback = CpuUsageLifecycleCallbacks { active = it }
        callback.onActivityPaused(mock())
        assertThat(active).isFalse()
    }
}
