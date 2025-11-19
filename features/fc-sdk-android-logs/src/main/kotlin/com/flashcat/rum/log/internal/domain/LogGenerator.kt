/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.log.internal.domain

import com.flashcat.rum.api.context.AccountInfo
import com.flashcat.rum.api.context.FlashcatContext
import com.flashcat.rum.api.context.NetworkInfo
import com.flashcat.rum.api.context.UserInfo
import com.flashcat.rum.core.feature.event.ThreadDump
import com.flashcat.rum.log.model.LogEvent
import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation
internal interface LogGenerator {

    @Suppress("LongParameterList")
    fun generateLog(
        level: Int,
        message: String,
        throwable: Throwable?,
        attributes: Map<String, Any?>,
        tags: Set<String>,
        timestamp: Long,
        threadName: String,
        flashcatContext: FlashcatContext,
        attachNetworkInfo: Boolean,
        loggerName: String,
        bundleWithTraces: Boolean = true,
        bundleWithRum: Boolean = true,
        userInfo: UserInfo? = null,
        accountInfo: AccountInfo? = null,
        networkInfo: NetworkInfo? = null,
        threads: List<ThreadDump> = emptyList()
    ): LogEvent?

    @Suppress("LongParameterList")
    fun generateLog(
        level: Int,
        message: String,
        errorKind: String?,
        errorMessage: String?,
        errorStack: String?,
        attributes: Map<String, Any?>,
        tags: Set<String>,
        timestamp: Long,
        threadName: String,
        flashcatContext: FlashcatContext,
        attachNetworkInfo: Boolean,
        loggerName: String,
        bundleWithTraces: Boolean = true,
        bundleWithRum: Boolean = true,
        userInfo: UserInfo? = null,
        accountInfo: AccountInfo? = null,
        networkInfo: NetworkInfo? = null
    ): LogEvent?
}
