/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.ndk

import com.flashcat.rum.api.feature.FeatureSdkCore
import com.flashcat.rum.ndk.internal.NdkCrashReportsFeature
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class NdkCrashReportsTest {

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger) doReturn mock()
    }

    @Test
    fun `M register ndk crash reports feature W enable()`() {
        // When
        NdkCrashReports.enable(mockSdkCore)

        // Then
        argumentCaptor<NdkCrashReportsFeature> {
            verify(mockSdkCore).registerFeature(capture())
        }
    }
}
