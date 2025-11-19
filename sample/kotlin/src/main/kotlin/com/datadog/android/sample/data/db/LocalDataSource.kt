/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sample.data.db

import android.content.Context
import com.flashcat.rum.sample.Preferences
import com.flashcat.rum.sample.data.db.room.RoomDataSource
import com.flashcat.rum.sample.data.db.sqldelight.SqlDelightDataSource
import com.flashcat.rum.sample.data.db.sqlite.SQLiteDataSource
import com.flashcat.rum.sample.data.model.Log
import com.flashcat.rum.sample.datalist.DataSourceType
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
