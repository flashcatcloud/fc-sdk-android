/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.sample

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cloud.flashcat.android.sample.data.DataRepository
import cloud.flashcat.android.sample.data.db.LocalDataSource
import cloud.flashcat.android.sample.data.remote.RemoteDataSource
import cloud.flashcat.android.sample.datalist.DataListViewModel
import cloud.flashcat.android.sample.traces.OtelTracesViewModel
import cloud.flashcat.android.sample.traces.TracesViewModel
import cloud.flashcat.android.sample.webview.WebViewModel
import cloud.flashcat.android.vendor.sample.LocalServer
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
