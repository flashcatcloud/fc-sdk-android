/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.sample.data.db.sqldelight

import android.content.Context
import cloud.flashcat.android.sample.LogsDatabase
import cloud.flashcat.android.sqldelight.DatadogSqliteCallback
import com.squareup.sqldelight.android.AndroidSqliteDriver

internal object Database {

    @Volatile
    private var INSTANCE: LogsDatabase? = null

    fun getInstance(context: Context): LogsDatabase =
        INSTANCE ?: synchronized(this) {
            INSTANCE ?: setupDb(context).also { INSTANCE = it }
        }

    private fun setupDb(context: Context): LogsDatabase {
        return LogsDatabase(
            AndroidSqliteDriver(
                LogsDatabase.Schema,
                context,
                callback = DatadogSqliteCallback(LogsDatabase.Schema)
            )
        )
    }
}
