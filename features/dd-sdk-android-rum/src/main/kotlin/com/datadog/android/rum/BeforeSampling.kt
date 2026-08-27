/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

/**
 * What the SDK is about to draw a new session with, handed to [BeforeSamplingCallback]: the rate
 * that would apply (the console's where it published one, the value passed to init where it did
 * not) and the console's custom values, decoded.
 *
 * @param sessionSampleRate the rate, between 0 and 100, that would decide this session.
 * @param custom the console's custom values, or null when remote configuration is off or nothing
 * is published. Same content as [RumMonitor.getRemoteConfig].
 */
data class BeforeSamplingContext(
    val sessionSampleRate: Float,
    val custom: Map<String, Any?>?
)

/**
 * The application's last word on session sampling, called synchronously each time a new session is
 * about to be drawn.
 *
 * Return a rate to override the one the SDK was going to use — 100 always collects, 0 never does —
 * or null to leave it alone. The typical use is an allow-list: keep every session of the handful of
 * users you are debugging while the fleet stays at a low rate.
 *
 * It runs inside session creation, so it must be fast and must not block. A throw, or a rate
 * outside 0..100, is ignored and the incoming rate applies: a mistake here must never take a
 * customer's collection down with it. A session already under way is never re-decided.
 */
fun interface BeforeSamplingCallback {

    /**
     * @param context the rate that would apply and the console's custom values.
     * @return the rate to draw this session with, or null to keep [BeforeSamplingContext.sessionSampleRate].
     */
    fun sampleRate(context: BeforeSamplingContext): Float?
}
