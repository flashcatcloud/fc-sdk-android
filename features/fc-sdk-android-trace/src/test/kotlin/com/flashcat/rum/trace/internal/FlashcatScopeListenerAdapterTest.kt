/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.flashcat.rum.trace.internal

import com.flashcat.rum.trace.api.scope.DatadogScopeListener
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify

@Extensions(
    ExtendWith(MockitoExtension::class)
)
class DatadogScopeListenerAdapterTest {

    @Mock
    lateinit var delegate: DatadogScopeListener

    @Test
    fun `M delegate afterScopeClosed W afterScopeClosed is called`() {
        // Given
        val adapter = DatadogScopeListenerAdapter(delegate)

        // When
        adapter.afterScopeClosed()

        // Then
        verify(delegate).afterScopeClosed()
    }

    @Test
    fun `M delegate afterScopeActivated W afterScopeActivated is called`() {
        // Then
        val adapter = DatadogScopeListenerAdapter(delegate)

        // When
        adapter.afterScopeActivated()

        // Then
        verify(delegate).afterScopeActivated()
    }
}
