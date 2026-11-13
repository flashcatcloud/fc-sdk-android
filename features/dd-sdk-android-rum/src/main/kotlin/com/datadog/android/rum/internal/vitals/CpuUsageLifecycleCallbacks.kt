/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.vitals

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.utils.executeSafe
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.Executor

/**
 * Only resumed (interactive) activities contribute, matching iOS active foreground periods.
 * Transitions run on [executor] because closing an interval reads `/proc`, which must stay off the main thread.
 */
internal class CpuUsageLifecycleCallbacks(
    private val executor: Executor,
    private val internalLogger: InternalLogger,
    private val setForeground: (Boolean) -> Unit
) : Application.ActivityLifecycleCallbacks {
    private val resumed = Collections.newSetFromMap(IdentityHashMap<Activity, Boolean>())

    override fun onActivityResumed(activity: Activity) {
        resumed.add(activity)
        post(true)
    }

    override fun onActivityPaused(activity: Activity) {
        resumed.remove(activity)
        // Also handles an SDK enabled after this activity's onResume was already delivered.
        if (resumed.isEmpty()) post(false)
    }

    override fun onActivityStopped(activity: Activity) = onActivityPaused(activity)
    override fun onActivityDestroyed(activity: Activity) = onActivityPaused(activity)
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    private fun post(active: Boolean) {
        executor.executeSafe("CPU usage foreground change", internalLogger) { setForeground(active) }
    }
}
