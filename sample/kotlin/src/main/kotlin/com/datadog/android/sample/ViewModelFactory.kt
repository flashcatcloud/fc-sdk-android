/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sample

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.flashcat.rum.sample.data.DataRepository
import com.flashcat.rum.sample.data.db.LocalDataSource
import com.flashcat.rum.sample.data.remote.RemoteDataSource
import com.flashcat.rum.sample.datalist.DataListViewModel
import com.flashcat.rum.sample.traces.OtelTracesViewModel
import com.flashcat.rum.sample.traces.TracesViewModel
import com.flashcat.rum.sample.webview.WebViewModel
import com.flashcat.rum.vendor.sample.LocalServer
import okhttp3.OkHttpClient

internal class ViewModelFactory(
    private val okHttpClient: OkHttpClient,
    private val remoteDataSource: RemoteDataSource,
    private val localDataSource: LocalDataSource,
    private val localServer: LocalServer
) :
    ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when (modelClass) {
            TracesViewModel::class.java -> {
                TracesViewModel(okHttpClient, localServer) as T
            }
            DataListViewModel::class.java -> {
                DataListViewModel(
                    DataRepository(remoteDataSource, localDataSource)
                ) as T
            }
            WebViewModel::class.java -> {
                WebViewModel(localServer) as T
            }
            OtelTracesViewModel::class.java -> {
                OtelTracesViewModel(okHttpClient, localServer) as T
            }
            else -> {
                modelClass.newInstance()
            }
        }
    }
}
