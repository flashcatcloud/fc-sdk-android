/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.remoteconfig

import android.app.Activity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.mock

@ExtendWith(MockitoExtension::class)
internal class ProcessForegroundCallbackTest {

    private var foregroundCount = 0
    private lateinit var testedCallback: ProcessForegroundCallback

    @BeforeEach
    fun setUp() {
        foregroundCount = 0
        testedCallback = ProcessForegroundCallback { foregroundCount++ }
    }

    @Test
    fun `M report the foreground W the first activity starts`() {
        testedCallback.onActivityStarted(mock<Activity>())

        assertThat(foregroundCount).isOne()
    }

    @Test
    fun `M report nothing W navigating between activities`() {
        // The next activity starts before the previous one stops, so the process never left the
        // foreground and there is nothing to refresh.
        val first = mock<Activity>()
        val second = mock<Activity>()
        testedCallback.onActivityStarted(first)
        testedCallback.onActivityStarted(second)
        testedCallback.onActivityStopped(first)

        assertThat(foregroundCount).isOne()
    }

    @Test
    fun `M report the foreground again W the app comes back after leaving`() {
        val activity = mock<Activity>()
        testedCallback.onActivityStarted(activity)
        testedCallback.onActivityStopped(activity)
        testedCallback.onActivityStarted(activity)

        assertThat(foregroundCount).isEqualTo(2)
    }

    @Test
    fun `M report a foreground W the SDK was registered while an activity was already started`() {
        // An app that initialises the SDK from an activity - the usual shape when initialisation
        // waits on a consent prompt - has one running before this callback exists, so the first
        // stop it sees has no matching start. Without a floor the count would go negative and could
        // never reach the one that means "the app is in the foreground again", leaving the callback
        // dead for the rest of the process.
        val activity = mock<Activity>()
        testedCallback.onActivityStopped(activity)

        // When the user leaves and comes back
        testedCallback.onActivityStarted(activity)

        // Then
        assertThat(foregroundCount).isOne()
    }
}
