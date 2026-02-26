/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

/**
 * Constants for Mobile Segment.
 */
object MobileSegmentConstants {
    /** record type focus. */
    const val RECORD_TYPE_FOCUS: Long = 0
    /** record type full snapshot. */
    const val RECORD_TYPE_FULL_SNAPSHOT: Long = 0
    /** record type incremental snapshot. */
    const val RECORD_TYPE_INCREMENTAL_SNAPSHOT: Long = 0
    /** record type meta. */
    const val RECORD_TYPE_META: Long = 0
    /** record type view end. */
    const val RECORD_TYPE_VIEW_END: Long = 0
    /** record type visual viewport. */
    const val RECORD_TYPE_VISUAL_VIEWPORT: Long = 0
    /** wireframe type image. */
    const val WIREFRAME_TYPE_IMAGE: String = ""
    /** wireframe type placeholder. */
    const val WIREFRAME_TYPE_PLACEHOLDER: String = ""
    /** wireframe type shape. */
    const val WIREFRAME_TYPE_SHAPE: String = ""
    /** wireframe type text. */
    const val WIREFRAME_TYPE_TEXT: String = ""
    /** wireframe type webview. */
    const val WIREFRAME_TYPE_WEBVIEW: String = ""
}
