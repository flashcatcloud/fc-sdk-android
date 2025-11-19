/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sessionreplay.internal.recorder

import android.content.Context
import android.view.Window
import com.flashcat.rum.api.InternalLogger
import com.flashcat.rum.internal.time.TimeProvider
import com.flashcat.rum.sessionreplay.ImagePrivacy
import com.flashcat.rum.sessionreplay.TextAndInputPrivacy
import com.flashcat.rum.sessionreplay.internal.TouchPrivacyManager
import com.flashcat.rum.sessionreplay.internal.async.RecordedDataQueueHandler
import com.flashcat.rum.sessionreplay.internal.recorder.callback.NoOpWindowCallback
import com.flashcat.rum.sessionreplay.internal.recorder.callback.RecorderWindowCallback
import com.flashcat.rum.sessionreplay.internal.utils.RumContextProvider
import java.util.WeakHashMap

internal class WindowCallbackInterceptor(
    private val recordedDataQueueHandler: RecordedDataQueueHandler,
    private val viewOnDrawInterceptor: ViewOnDrawInterceptor,
    private val timeProvider: TimeProvider,
    private val rumContextProvider: RumContextProvider,
    private val internalLogger: InternalLogger,
    private val imagePrivacy: ImagePrivacy,
    private val textAndInputPrivacy: TextAndInputPrivacy,
    private val touchPrivacyManager: TouchPrivacyManager
) {
    private val wrappedWindows: WeakHashMap<Window, Any?> = WeakHashMap()

    fun intercept(windows: List<Window>, appContext: Context) {
        windows.forEach { window ->
            wrapWindowCallback(window, appContext)
            wrappedWindows[window] = null
        }
    }

    fun stopIntercepting(windows: List<Window>) {
        windows.forEach {
            unwrapWindowCallback(it)
            wrappedWindows.remove(it)
        }
    }

    fun stopIntercepting() {
        wrappedWindows.entries.forEach {
            unwrapWindowCallback(it.key)
        }
        wrappedWindows.clear()
    }

    private fun wrapWindowCallback(window: Window, appContext: Context) {
        val toWrap = window.callback ?: NoOpWindowCallback()
        window.callback = RecorderWindowCallback(
            appContext,
            recordedDataQueueHandler,
            toWrap,
            timeProvider,
            rumContextProvider,
            viewOnDrawInterceptor,
            internalLogger,
            textAndInputPrivacy,
            imagePrivacy,
            touchPrivacyManager
        )
    }

    private fun unwrapWindowCallback(window: Window) {
        val callback = window.callback
        if (callback is RecorderWindowCallback) {
            val wrappedCallback = callback.wrappedCallback
            if (wrappedCallback !is NoOpWindowCallback) {
                window.callback = wrappedCallback
            } else {
                window.callback = null
            }
        }
    }
}
