/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.sample.automotive

import cloud.flashcat.android.log.Logger

@Suppress("UndocumentedPublicClass")
object SharedLogger {
    @Suppress("UndocumentedPublicProperty")
    val logger by lazy {
        Logger.Builder()
            .setLogcatLogsEnabled(true)
            .build()
    }
}
