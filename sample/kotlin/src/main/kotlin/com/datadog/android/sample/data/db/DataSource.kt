/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sample.data.db

import com.flashcat.rum.sample.data.model.Log
import com.flashcat.rum.sample.datalist.DataSourceType
import io.reactivex.rxjava3.core.SingleSource

internal interface DataSource {
    fun fetchLogs(): SingleSource<List<Log>>

    fun persistLogs(logs: List<Log>)

    val type: DataSourceType
}
