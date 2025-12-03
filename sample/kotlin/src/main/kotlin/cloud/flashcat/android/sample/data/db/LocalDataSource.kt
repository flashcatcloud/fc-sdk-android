/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.sample.data.db

import android.content.Context
import cloud.flashcat.android.sample.Preferences
import cloud.flashcat.android.sample.data.db.room.RoomDataSource
import cloud.flashcat.android.sample.data.db.sqldelight.SqlDelightDataSource
import cloud.flashcat.android.sample.data.db.sqlite.SQLiteDataSource
import cloud.flashcat.android.sample.data.model.Log
import cloud.flashcat.android.sample.datalist.DataSourceType
import io.reactivex.rxjava3.core.SingleSource

internal class LocalDataSource(
    val context: Context
) {

    private val dataSource: DataSource by lazy {
        val type = Preferences.defaultPreferences(context)
            .getLocalDataSource()

        when (type) {
            DataSourceType.ROOM -> RoomDataSource(context)
            DataSourceType.SQLDELIGHT -> SqlDelightDataSource(context)
            DataSourceType.SQLITE -> SQLiteDataSource(context)
        }
    }

    fun fetchLogs(): SingleSource<List<Log>> {
        return dataSource.fetchLogs()
    }

    fun persistLogs(logs: List<Log>) {
        dataSource.persistLogs(logs)
    }

    fun getType(): DataSourceType {
        return dataSource.type
    }

    fun setType(type: DataSourceType) {
        Preferences.defaultPreferences(context).setLocalDataSource(type)
    }
}
