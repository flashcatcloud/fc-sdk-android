/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.glide

import com.bumptech.glide.load.engine.executor.GlideExecutor
import com.flashcat.rum.api.InternalLogger
import com.flashcat.rum.core.SdkReference
import com.flashcat.rum.rum.GlobalRumMonitor
import com.flashcat.rum.rum.RumErrorSource
import com.flashcat.rum.rum.RumMonitor

/**
 * A [GlideExecutor.UncaughtThrowableStrategy] implementation that will forward all errors
 * to the active [RumMonitor].
 *
 * @param name the name of the feature this strategy will be used for
 * (e.g.: "Disk Cache", "Source", …)
 * @param sdkInstanceName the SDK instance name to bind to, or null to check the default instance.
 * Instrumentation won't be working until SDK instance is ready.
 */
class DatadogRUMUncaughtThrowableStrategy(
    val name: String,
    private val sdkInstanceName: String? = null
) : GlideExecutor.UncaughtThrowableStrategy {

    private val sdkReference = SdkReference(sdkInstanceName)

    // region GlideExecutor.UncaughtThrowableStrategy

    /** @inheritdoc */
    override fun handle(t: Throwable?) {
        if (t != null) {
            val sdkCore = sdkReference.get()
            if (sdkCore != null) {
                GlobalRumMonitor.get(sdkCore)
                    .addError("Glide $name error", RumErrorSource.SOURCE, t)
            } else {
                val prefix = if (sdkInstanceName == null) {
                    "Default SDK instance"
                } else {
                    "SDK instance with name=$sdkInstanceName"
                }
                InternalLogger.UNBOUND.log(
                    InternalLogger.Level.INFO,
                    InternalLogger.Target.USER,
                    {
                        "$prefix is not provided, skipping reporting the Glide $name error"
                    }
                )
            }
        }
    }

    // endregion
}
