/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.annotation.RestrictTo

/** Process-local activity state, registered before the first activity by DdRumContentProvider. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
object ForegroundStateTracker : Application.ActivityLifecycleCallbacks {
    private const val TAG = "DD_ForegroundState"
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
        notifyListener(listener, activities.isNotEmpty())
    }

    @Synchronized
    fun removeListener(listener: (Boolean) -> Unit) { listeners.remove(listener) }

    @Synchronized
    override fun onActivityStarted(activity: Activity) {
        val wasEmpty = activities.isEmpty()
        activities.add(activity)
        if (wasEmpty && activities.isNotEmpty()) notifyListeners(true)
    }

    @Synchronized
    override fun onActivityStopped(activity: Activity) {
        val removed = activities.remove(activity)
        if (removed && activities.isEmpty()) notifyListeners(false)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = onActivityStopped(activity)

    private fun notifyListeners(foreground: Boolean) {
        listeners.toList().forEach { notifyListener(it, foreground) }
    }

    // A failing listener (active beacon) must not abort the host Activity lifecycle callback.
    private fun notifyListener(listener: (Boolean) -> Unit, foreground: Boolean) {
        try {
            listener(foreground)
        } catch (e: Exception) {
            Log.e(TAG, "A foreground listener threw an exception.", e)
        }
    }
}
