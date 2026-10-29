/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum

import android.app.Activity
import android.app.Application
import android.os.Bundle

/** Process-local activity state, registered before the first activity by DdRumContentProvider. */
object ForegroundStateTracker : Application.ActivityLifecycleCallbacks {
    private var registered = false
    private val activities = mutableSetOf<Activity>()
    private val listeners = mutableSetOf<(Boolean) -> Unit>()

    @Synchronized
    fun register(application: Application) {
        if (!registered) {
            application.registerActivityLifecycleCallbacks(this)
            registered = true
        }
    }

    @Synchronized
    fun addListener(listener: (Boolean) -> Unit) {
        listeners.add(listener)
        listener(activities.isNotEmpty())
    }

    @Synchronized
    fun removeListener(listener: (Boolean) -> Unit) { listeners.remove(listener) }

    @Synchronized
    override fun onActivityStarted(activity: Activity) {
        val wasEmpty = activities.isEmpty()
        activities.add(activity)
        if (wasEmpty && activities.isNotEmpty()) listeners.toList().forEach { it(true) }
    }

    @Synchronized
    override fun onActivityStopped(activity: Activity) {
        val removed = activities.remove(activity)
        if (removed && activities.isEmpty()) listeners.toList().forEach { it(false) }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = onActivityStopped(activity)
}
