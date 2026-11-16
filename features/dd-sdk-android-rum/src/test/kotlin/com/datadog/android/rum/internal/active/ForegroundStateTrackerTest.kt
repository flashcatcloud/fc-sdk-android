/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.internal.active

import android.app.Activity
import android.app.ActivityManager.RunningAppProcessInfo
import com.datadog.android.rum.DdRumContentProvider
import com.datadog.android.rum.ForegroundStateTracker
import com.datadog.tools.unit.setStaticValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class ForegroundStateTrackerTest {
    private val initialProcessImportance = DdRumContentProvider.processImportance

    @AfterEach
    fun `tear down`() {
        DdRumContentProvider.processImportance = initialProcessImportance
    }

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

    @Test
    fun `rotation is not reported as a background transition`() {
        val old: Activity = mock()
        val recreated: Activity = mock()
        whenever(old.isChangingConfigurations).thenReturn(true)
        val values = mutableListOf<Boolean>()
        val listener: (Boolean) -> Unit = { values.add(it) }
        ForegroundStateTracker.addListener(listener)
        ForegroundStateTracker.onActivityStarted(old)
        ForegroundStateTracker.onActivityStopped(old)
        ForegroundStateTracker.onActivityDestroyed(old)
        ForegroundStateTracker.onActivityStarted(recreated)
        ForegroundStateTracker.onActivityStopped(recreated)
        ForegroundStateTracker.removeListener(listener)
        assertThat(values).containsExactly(false, true, false)
    }

    @Test
    fun `stop of an activity started before registration keeps a visible activity in the foreground`() {
        val early: Activity = mock()
        val visible: Activity = mock()
        val values = mutableListOf<Boolean>()
        val listener: (Boolean) -> Unit = { values.add(it) }
        ForegroundStateTracker.addListener(listener)
        ForegroundStateTracker.onActivityStarted(visible)
        ForegroundStateTracker.onActivityStopped(early)
        ForegroundStateTracker.onActivityStopped(visible)
        ForegroundStateTracker.removeListener(listener)
        assertThat(values).containsExactly(false, true, false)
    }

    @Test
    fun `failing listener does not stop other listeners`() {
        val activity: Activity = mock()
        val values = mutableListOf<Boolean>()
        val failing: (Boolean) -> Unit = { throw IllegalStateException("boom") }
        val listener: (Boolean) -> Unit = { values.add(it) }
        ForegroundStateTracker.addListener(failing)
        ForegroundStateTracker.addListener(listener)
        ForegroundStateTracker.onActivityStarted(activity)
        ForegroundStateTracker.onActivityStopped(activity)
        ForegroundStateTracker.removeListener(failing)
        ForegroundStateTracker.removeListener(listener)
        assertThat(values).containsExactly(false, true, false)
    }

    @Test
    fun `process launched in the foreground is not in the background before its first activity`() {
        val activity: Activity = mock()
        DdRumContentProvider.processImportance = RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        resetToProcessStart()
        assertThat(ForegroundStateTracker.isInBackground()).isFalse()
        ForegroundStateTracker.onActivityStarted(activity)
        assertThat(ForegroundStateTracker.isInBackground()).isFalse()
        ForegroundStateTracker.onActivityStopped(activity)
        assertThat(ForegroundStateTracker.isInBackground()).isTrue()
        assertThat(ForegroundStateTracker.backgroundTransitions).isEqualTo(1)
    }

    @Test
    fun `process launched in the background is in the background until an activity starts`() {
        val activity: Activity = mock()
        DdRumContentProvider.processImportance = RunningAppProcessInfo.IMPORTANCE_SERVICE
        resetToProcessStart()
        assertThat(ForegroundStateTracker.isInBackground()).isTrue()
        ForegroundStateTracker.onActivityStarted(activity)
        assertThat(ForegroundStateTracker.isInBackground()).isFalse()
        ForegroundStateTracker.onActivityStopped(activity)
        assertThat(ForegroundStateTracker.isInBackground()).isTrue()
        assertThat(ForegroundStateTracker.backgroundTransitions).isEqualTo(1)
    }

    @Test
    fun `activity being created is not in the background`() {
        val activity: Activity = mock()
        val finished: Activity = mock()
        DdRumContentProvider.processImportance = RunningAppProcessInfo.IMPORTANCE_SERVICE
        resetToProcessStart()
        ForegroundStateTracker.onActivityCreated(activity, null)
        assertThat(ForegroundStateTracker.isInBackground()).isFalse()
        ForegroundStateTracker.onActivityStarted(activity)
        ForegroundStateTracker.onActivityStopped(activity)
        assertThat(ForegroundStateTracker.isInBackground()).isTrue()
        ForegroundStateTracker.onActivityCreated(finished, null)
        assertThat(ForegroundStateTracker.isInBackground()).isFalse()
        ForegroundStateTracker.onActivityDestroyed(finished)
        assertThat(ForegroundStateTracker.isInBackground()).isTrue()
        ForegroundStateTracker.onActivityDestroyed(activity)
    }

    @Test
    fun `rotation does not count as a background transition`() {
        val old: Activity = mock()
        val recreated: Activity = mock()
        whenever(old.isChangingConfigurations).thenReturn(true)
        ForegroundStateTracker.onActivityStarted(old)
        val transitions = ForegroundStateTracker.backgroundTransitions
        ForegroundStateTracker.onActivityStopped(old)
        ForegroundStateTracker.onActivityStarted(recreated)
        assertThat(ForegroundStateTracker.backgroundTransitions).isEqualTo(transitions)
        assertThat(ForegroundStateTracker.isInBackground()).isFalse()
        ForegroundStateTracker.onActivityStopped(recreated)
        assertThat(ForegroundStateTracker.backgroundTransitions).isEqualTo(transitions + 1)
    }

    // The tracker is process-wide; other tests leave it in the background with transitions already counted.
    private fun resetToProcessStart() {
        ForegroundStateTracker::class.java.setStaticValue("backgroundTransitions", 0)
    }
}
