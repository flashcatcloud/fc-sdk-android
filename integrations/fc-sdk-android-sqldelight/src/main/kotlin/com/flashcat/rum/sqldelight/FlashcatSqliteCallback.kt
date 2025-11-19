/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sqldelight

import androidx.sqlite.db.SupportSQLiteDatabase
import com.flashcat.rum.Flashcat
import com.flashcat.rum.api.SdkCore
import com.flashcat.rum.rum.GlobalRumMonitor
import com.flashcat.rum.rum.RumAttributes
import com.flashcat.rum.rum.RumErrorSource
import com.squareup.sqldelight.android.AndroidSqliteDriver
import com.squareup.sqldelight.db.SqlDriver
import java.util.Locale

/**
 * Extends the [AndroidSqliteDriver.Callback] to intercept any Database corruption callback and to
 * automatically send a RUM error event whenever this issue occurs.
 *
 * For more information [https://www.sqlite.org/howtocorrupt.html]
 * @param schema the SQL database schema
 * @param sdkCore the SDK instance to forward the errors to. If not provided, default instance
 * will be used.
 */
class DatadogSqliteCallback
@JvmOverloads
constructor(
    schema: SqlDriver.Schema,
    private val sdkCore: SdkCore = Datadog.getInstance()
) : AndroidSqliteDriver.Callback(schema) {

    /** @inheritDoc */
    override fun onCorruption(db: SupportSQLiteDatabase) {
        super.onCorruption(db)
        GlobalRumMonitor.get(sdkCore)
            .addError(
                String.format(
                    Locale.US,
                    DATABASE_CORRUPTION_ERROR_MESSAGE,
                    db.path
                ),
                RumErrorSource.SOURCE,
                null,
                mapOf(
                    RumAttributes.ERROR_DATABASE_PATH to db.path,
                    RumAttributes.ERROR_DATABASE_VERSION to db.version
                )
            )
    }

    internal companion object {
        internal const val DATABASE_CORRUPTION_ERROR_MESSAGE =
            "Corruption reported by sqlite database: %s"
    }
}
