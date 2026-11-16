/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.annotation.RestrictTo
import java.util.Collections
import java.util.WeakHashMap

/** Process-local activity state, registered before the first activity by DdRumContentProvider. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
object ForegroundStateTracker : Application.ActivityLifecycleCallbacks {
    private const val TAG = "DD_ForegroundState"
    private var registered = false

    // Weak, so the process-wide object never keeps an activity alive. Membership (unlike a counter) ignores
    // the stop of an activity started before registration while another one is still visible.
    private val started: MutableSet<Activity> = Collections.newSetFromMap(WeakHashMap())

    // Created but not yet started: an activity about to be shown, so work done in its onCreate is not background.
    private val pending: MutableSet<Activity> = Collections.newSetFromMap(WeakHashMap())
    private var foreground = false
    private val listeners = mutableSetOf<(Boolean) -> Unit>()

    /** Number of foreground to background transitions since process start; it only increases. */
    @Volatile
    var backgroundTransitions: Int = 0
        private set

    @Synchronized
    fun register(application: Application) {
        if (!registered) {
            application.registerActivityLifecycleCallbacks(this)
            registered = true
        }
    }

    /**
     * Whether the process is in the background: no activity is started or about to start. A process launched in
     * the foreground is not in the background before its first activity starts; a process launched in the
     * background (e.g. by a broadcast) is, until an activity is created.
     */
    @Synchronized
    fun isInBackground(): Boolean =
        !foreground && pending.isEmpty() && (backgroundTransitions > 0 || launchedInBackground())

    @Synchronized
    fun addListener(listener: (Boolean) -> Unit) {
        listeners.add(listener)
        notifyListener(listener, foreground)
    }

    @Synchronized
    fun removeListener(listener: (Boolean) -> Unit) { listeners.remove(listener) }

    @Synchronized
    override fun onActivityStarted(activity: Activity) {
        pending.remove(activity)
        started.add(activity)
        if (!foreground) {
            foreground = true
            notifyListeners(true)
        }
    }

    @Synchronized
    override fun onActivityStopped(activity: Activity) {
        val removed = started.remove(activity)
        // A rotation stops the old activity before the new one starts; it is not a background transition.
        if (removed && started.isEmpty() && foreground && !activity.isChangingConfigurations) {
            foreground = false
            backgroundTransitions++
            notifyListeners(false)
        }
    }

    @Synchronized
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        pending.add(activity)
    }

    @Synchronized
    override fun onActivityDestroyed(activity: Activity) {
        // An activity finished from its onCreate is never started or stopped. If it was the last one about to be
        // shown, the process leaves the screen here: count it, which also ends the foreground launch exemption.
        val removed = pending.remove(activity)
        if (removed && pending.isEmpty() && started.isEmpty() && !activity.isChangingConfigurations) {
            backgroundTransitions++
        }
    }

    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    // 0 means the importance is unknown (DdRumContentProvider did not run); treat it as a foreground launch.
    private fun launchedInBackground(): Boolean {
        val importance = DdRumContentProvider.processImportance
        return importance != 0 && importance != ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

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
