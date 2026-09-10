/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.remoteconfig

/**
 * FLASHCAT FORK - which console configuration a session was drawn under. Events carry it so an
 * auditor can recover the exact settings from the console's version history — a session is never
 * re-judged, so the version must be the one in force at its creation, not whatever has arrived
 * since.
 *
 * Only the version lives here. The rate the draw actually used travels down the scope chain as
 * `sampleRate` and is what every event already reports, so keeping a second copy of it would be
 * two records of one fact.
 */
internal data class DrawnConfiguration(
    /** The remote settings version the draw read, or 0 when none was ever fetched. */
    val version: Int
)
