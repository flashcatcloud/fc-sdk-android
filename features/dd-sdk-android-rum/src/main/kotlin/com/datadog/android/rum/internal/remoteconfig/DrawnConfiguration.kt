/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.remoteconfig

/**
 * FLASHCAT FORK - the configuration a session was drawn under: the rate actually used at the draw
 * (the console's where it set one, the init value where it did not) and the remote settings
 * version it came from. Events carry these instead of the init values, so server-side
 * extrapolation and audits line up with the draw that kept the session — a session is never
 * re-judged, so the metadata must be from its creation, not from whatever has arrived since.
 */
internal data class DrawnConfiguration(
    /** The session this record belongs to, so a record can never be read against another one. */
    val sessionId: String,
    /** The remote settings version the draw read, or 0 when none was ever fetched. */
    val version: Int,
    val sessionSampleRate: Float
)
