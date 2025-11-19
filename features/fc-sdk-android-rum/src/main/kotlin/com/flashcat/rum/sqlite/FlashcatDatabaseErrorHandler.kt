/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sqlite

import android.database.DatabaseErrorHandler
import android.database.DefaultDatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import com.flashcat.rum.api.InternalLogger
import com.flashcat.rum.core.SdkReference
import com.flashcat.rum.rum.GlobalRumMonitor
import com.flashcat.rum.rum.RumAttributes
import com.flashcat.rum.rum.RumErrorSource
import java.util.Locale

/**
 * Provides an implementation of [DatadogDatabaseErrorHandler] already set up to send
 * relevant information to Datadog.
 *
 * It will automatically send RUM Error events whenever a Database corruption was signaled.
 * For more information [https://www.sqlite.org/howtocorrupt.html]
 *
 * @param sdkInstanceName the SDK instance name to bind to, or null to check the default instance.
 * Instrumentation won't be working until SDK instance is ready.
 * @param defaultErrorHandler the corruption error handler, by default it is [DefaultDatabaseErrorHandler].
 */
class DatadogDatabaseErrorHandler(
    private val sdkInstanceName: String? = null,
    internal val defaultErrorHandler: DatabaseErrorHandler = DefaultDatabaseErrorHandler()
) : DatabaseErrorHandler {

    private val sdkReference = SdkReference(sdkInstanceName)

    /** @inheritDoc */
    override fun onCorruption(dbObj: SQLiteDatabase) {
        defaultErrorHandler.onCorruption(dbObj)
        val sdkCore = sdkReference.get()
        if (sdkCore != null) {
            GlobalRumMonitor.get(sdkCore)
                .addError(
                    String.format(Locale.US, DATABASE_CORRUPTION_ERROR_MESSAGE, dbObj.path),
                    RumErrorSource.SOURCE,
                    null,
                    mapOf(
                        RumAttributes.ERROR_DATABASE_PATH to dbObj.path,
                        RumAttributes.ERROR_DATABASE_VERSION to dbObj.version
                    )
                )
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
                    "$prefix is not found, " +
                        "skipping reporting the corruption of sqlite database: %s"
                }
            )
        }
    }

    internal companion object {
        internal const val DATABASE_CORRUPTION_ERROR_MESSAGE =
            "Corruption reported by sqlite database: %s"
    }
}
