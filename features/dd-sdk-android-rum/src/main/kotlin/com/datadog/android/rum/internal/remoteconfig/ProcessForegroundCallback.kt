/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.remoteconfig

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.annotation.MainThread
import java.util.concurrent.atomic.AtomicInteger

/**
 * Calls back when the process comes to the foreground, having had no started activity before.
 *
 * An app spends most of its life in the background, where a poll timer is unreliable: the system
 * may not run it for hours. Asking again on the way back in is what stops someone reopening the app
 * and carrying on under settings that were changed while it was away — without the app having to
 * call anything itself.
 *
 * Rotations and activity-to-activity navigation keep the counter above zero, so neither is mistaken
 * for a return to the foreground.
 */
internal class ProcessForegroundCallback(
    private val onForeground: () -> Unit
) : Application.ActivityLifecycleCallbacks {

    private val startedActivities = AtomicInteger(0)

    @MainThread
    override fun onActivityStarted(activity: Activity) {
        if (startedActivities.incrementAndGet() == 1) {
            onForeground()
        }
    }

    @MainThread
    override fun onActivityStopped(activity: Activity) {
        startedActivities.decrementAndGet()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
