/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.internal.active

import android.app.Activity
import com.datadog.android.rum.ForegroundStateTracker
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

internal class ForegroundStateTrackerTest {
    @Test
    fun `late subscribers receive activity state while background processes remain inactive`() {
        val activity: Activity = mock()
        val another: Activity = mock()
        val values = mutableListOf<Boolean>()
        val listener: (Boolean) -> Unit = { values.add(it) }
        ForegroundStateTracker.onActivityStarted(activity)
        ForegroundStateTracker.addListener(listener)
        ForegroundStateTracker.onActivityStarted(another)
        ForegroundStateTracker.onActivityStopped(activity)
        ForegroundStateTracker.onActivityStopped(another)
        ForegroundStateTracker.removeListener(listener)
        assertThat(values).containsExactly(true, false)
        val background = mutableListOf<Boolean>()
        val next: (Boolean) -> Unit = { background.add(it) }
        ForegroundStateTracker.addListener(next)
        ForegroundStateTracker.removeListener(next)
        assertThat(background).containsExactly(false)
    }
}
