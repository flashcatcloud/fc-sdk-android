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
 * Activity-to-activity navigation keeps the counter above zero, so it is not mistaken for a return
 * to the foreground. A rotation is not covered: the system destroys the old activity before it
 * creates the new one, so the count really does reach zero and the app really does look like it
 * left. That costs at most one extra request, and only when the stored ttl says the configuration
 * is stale anyway, so it is left alone rather than given a state machine of its own.
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
        // Floored at zero, because the count starts from nothing while the app may already have
        // started an activity: an app that initialises the SDK from an activity - the usual shape
        // when initialisation waits on a consent prompt - has one running by the time this is
        // registered. Without the floor that activity's stop would take the count negative, and it
        // could never climb back to the one that means "the app is in the foreground again": this
        // callback would be dead for the rest of the process.
        //
        // Read-then-decrement needs no atomic update because both callbacks arrive on the main
        // thread; the counter is atomic only because [refreshIfStale] reads its effect elsewhere.
        if (startedActivities.get() > 0) {
            startedActivities.decrementAndGet()
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
