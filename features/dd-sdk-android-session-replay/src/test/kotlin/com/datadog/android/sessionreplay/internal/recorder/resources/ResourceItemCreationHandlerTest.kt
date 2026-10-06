/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.async.DataQueueHandler
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class ResourceItemCreationHandlerTest {
    private lateinit var testedHandler: ResourceItemCreationHandler

    @Mock
    lateinit var mockDataQueueHandler: DataQueueHandler

    @StringForgery
    lateinit var fakeResourceId: String

    @BeforeEach
    fun `set up`() {
        testedHandler = ResourceItemCreationHandler(
            recordedDataQueueHandler = mockDataQueueHandler
        )
    }

    @Test
    fun `M queue item W queueItem() { not previously seen }`() {
        // Given
        val fakeByteArray = fakeResourceId.toByteArray()

        // When
        testedHandler.queueItem(fakeResourceId, fakeByteArray)

        // Then
        verify(mockDataQueueHandler).addResourceItem(
            identifier = fakeResourceId,
            resourceData = fakeByteArray
        )
    }

    @Test
    fun `M queue item again W queueItem() { forgotten since }`() {
        // Given
        val fakeByteArray = fakeResourceId.toByteArray()
        testedHandler.queueItem(fakeResourceId, fakeByteArray)

        // When
        testedHandler.forget(listOf(fakeResourceId))
        val forgotten = testedHandler.isForgotten(fakeResourceId)
        testedHandler.queueItem(fakeResourceId, fakeByteArray)

        // Then
        assertThat(forgotten).isTrue
        assertThat(testedHandler.isForgotten(fakeResourceId)).isFalse
        verify(mockDataQueueHandler, times(2)).addResourceItem(
            identifier = fakeResourceId,
            resourceData = fakeByteArray
        )
    }

    @Test
    fun `M forget the oldest forgotten ids W forget() { over the limit }`() {
        // Given
        val ids = List(ResourceItemCreationHandler.FORGOTTEN_RESOURCE_IDS_LIMIT + 1) { "r$it" }

        // When
        testedHandler.forget(ids)

        // Then
        assertThat(testedHandler.isForgotten("r0")).isFalse
        assertThat(testedHandler.isForgotten(ids.last())).isTrue
        assertThat(testedHandler.forgottenResourceIds).hasSize(ResourceItemCreationHandler.FORGOTTEN_RESOURCE_IDS_LIMIT)
    }

    @Test
    fun `M not queue item W queueItem() { previously seen }`() {
        // Given
        val fakeByteArray = fakeResourceId.toByteArray()

        // When
        testedHandler.queueItem(fakeResourceId, fakeByteArray)
        testedHandler.queueItem(fakeResourceId, fakeByteArray)

        // Then
        verify(mockDataQueueHandler).addResourceItem(
            identifier = fakeResourceId,
            resourceData = fakeByteArray
        )
    }

    @Test
    fun `M add unique resourceId only once W queueItem()`() {
        // Given
        val fakeByteArray = fakeResourceId.toByteArray()

        // When
        testedHandler.queueItem(fakeResourceId, fakeByteArray)
        testedHandler.queueItem(fakeResourceId, fakeByteArray)

        // Then
        assertThat(testedHandler.resourceIdsSeen).hasSize(1)
    }
}
