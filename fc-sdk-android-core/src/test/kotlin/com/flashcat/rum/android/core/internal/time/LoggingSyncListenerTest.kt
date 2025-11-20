/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.core.internal.time

import com.flashcat.rum.api.InternalLogger
import com.flashcat.rum.utils.verifyLog
import com.flashcat.tools.unit.forge.aThrowable
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.kotlin.mock

@Extensions(
    ExtendWith(ForgeExtension::class)
)
internal class LoggingSyncListenerTest {

    @Test
    fun `M log error W onError()`(
        @StringForgery(regex = "https://[a-z]+\\.com") fakeHost: String,
        forge: Forge
    ) {
        // Given
        val mockInternalLogger = mock<InternalLogger>()
        val testableListener = LoggingSyncListener(internalLogger = mockInternalLogger)
        val throwable = forge.aThrowable()

        // When
        testableListener.onError(fakeHost, throwable)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            "Kronos onError @host:$fakeHost",
            throwable
        )
    }
}
