/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.trace.opentelemetry.internal

import android.os.Build
import androidx.annotation.RequiresApi
import io.opentelemetry.context.ContextStorage
import java.util.function.Function

@RequiresApi(Build.VERSION_CODES.N)
internal class FlashcatContextStorageWrapper : Function<ContextStorage, FlashcatContextStorage> {
    override fun apply(wrapped: ContextStorage): FlashcatContextStorage {
        return if (wrapped is FlashcatContextStorage) wrapped else FlashcatContextStorage(wrapped)
    }
}
