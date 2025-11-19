/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sessionreplay.internal

import android.app.Application
import com.flashcat.rum.sessionreplay.internal.recorder.Recorder
import com.flashcat.rum.sessionreplay.internal.resources.ResourceDataStoreManager
import com.flashcat.rum.sessionreplay.internal.storage.RecordWriter
import com.flashcat.rum.sessionreplay.internal.storage.ResourcesWriter
import com.flashcat.rum.sessionreplay.internal.utils.RumContextProvider

internal fun interface RecorderProvider {
    fun provideSessionReplayRecorder(
        resourceDataStoreManager: ResourceDataStoreManager,
        resourceWriter: ResourcesWriter,
        recordWriter: RecordWriter,
        rumContextProvider: RumContextProvider,
        application: Application
    ): Recorder
}
