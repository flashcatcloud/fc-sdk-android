/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose

import androidx.compose.ui.Modifier
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.TouchPrivacy

/**
 * Extension functions to hide/unhide a Composable from Session Replay.
 */
@Suppress("UNUSED_PARAMETER")
fun Modifier.sessionReplayHidden(hidden: Boolean): Modifier = this

/**
 * Extension functions to override the image privacy for a Composable in Session Replay.
 */
@Suppress("UNUSED_PARAMETER")
fun Modifier.sessionReplayImagePrivacy(imagePrivacy: ImagePrivacy): Modifier = this

/**
 * Extension functions to override the touch privacy for a Composable in Session Replay.
 */
@Suppress("UNUSED_PARAMETER")
fun Modifier.sessionReplayTouchPrivacy(touchPrivacy: TouchPrivacy): Modifier = this

/**
 * Extension functions to override the text and input privacy for a Composable in Session Replay.
 */
@Suppress("UNUSED_PARAMETER")
fun Modifier.sessionReplayTextAndInputPrivacy(textAndInputPrivacy: TextAndInputPrivacy): Modifier = this
