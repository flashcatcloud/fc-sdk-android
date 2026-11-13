/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.vitals

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.util.Collections
import java.util.IdentityHashMap

/** Only resumed (interactive) activities contribute, matching iOS active foreground periods. */
internal class CpuUsageLifecycleCallbacks(private val setForeground: (Boolean) -> Unit) :
    Application.ActivityLifecycleCallbacks {
    private val resumed = Collections.newSetFromMap(IdentityHashMap<Activity, Boolean>())

    override fun onActivityResumed(activity: Activity) {
        resumed.add(activity)
        setForeground(true)
    }

    override fun onActivityPaused(activity: Activity) {
        resumed.remove(activity)
        // Also handles an SDK enabled after this activity's onResume was already delivered.
        if (resumed.isEmpty()) setForeground(false)
    }

    override fun onActivityStopped(activity: Activity) = onActivityPaused(activity)
    override fun onActivityDestroyed(activity: Activity) = onActivityPaused(activity)
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
