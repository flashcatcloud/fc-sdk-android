/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import android.view.View

/**
 * Extension functions to hide/unhide a view from Session Replay.
 */
@Suppress("UNUSED_PARAMETER")
fun View.setSessionReplayHidden(hidden: Boolean) {
}

/**
 * Extension functions to override the image privacy for a view in Session Replay.
 */
@Suppress("UNUSED_PARAMETER")
fun View.setSessionReplayImagePrivacy(imagePrivacy: ImagePrivacy) {
}

/**
 * Extension functions to override the touch privacy for a view in Session Replay.
 */
@Suppress("UNUSED_PARAMETER")
fun View.setSessionReplayTouchPrivacy(touchPrivacy: TouchPrivacy) {
}

/**
 * Extension functions to override the text and input privacy for a view in Session Replay.
 */
@Suppress("UNUSED_PARAMETER")
fun View.setSessionReplayTextAndInputPrivacy(textAndInputPrivacy: TextAndInputPrivacy) {
}
