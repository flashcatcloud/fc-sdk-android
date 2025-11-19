/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.rum

import com.flashcat.rum.api.feature.FeatureSdkCore
import com.flashcat.rum.api.storage.datastore.DataStoreHandler
import com.flashcat.rum.core.persistence.datastore.DataStoreContent
import com.flashcat.rum.rum.internal.AnonymousIdentifierReadCallback
import com.flashcat.rum.rum.internal.RumAnonymousIdentifierManager
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.whenever
import java.util.UUID

@RunWith(MockitoJUnitRunner::class)
class RumAnonymousIdentifierManagerTest {

    @Mock
    private lateinit var dataStore: DataStoreHandler

    @Mock
    private lateinit var core: FeatureSdkCore

    private lateinit var rumAnonymousIdentifierManager: RumAnonymousIdentifierManager

    @Before
    fun setUp() {
        rumAnonymousIdentifierManager = RumAnonymousIdentifierManager(dataStore, core)
    }

    @Test
    fun `M generate new anonymous id W id not present and tracking enabled`() {
        // Given
        whenever(
            dataStore.value(
                eq("anonymous_id_key"),
                isNull(),
                any<AnonymousIdentifierReadCallback>(),
                any()
            )
        ).thenAnswer {
            val callback = it.arguments[2] as AnonymousIdentifierReadCallback
            callback.onSuccess(null)
        }

        // When
        rumAnonymousIdentifierManager.manageAnonymousId(true)

        // Then
        verify(dataStore).setValue(eq("anonymous_id_key"), any(), eq(0), isNull(), any())
        verify(core).setAnonymousId(any())
    }

    @Test
    fun `M do not generate new anonymous id W id present and tracking enabled`() {
        // Given
        whenever(
            dataStore.value(
                eq("anonymous_id_key"),
                isNull(),
                any<AnonymousIdentifierReadCallback>(),
                any()
            )
        ).thenAnswer {
            val callback = it.arguments[2] as AnonymousIdentifierReadCallback
            callback.onSuccess(DataStoreContent(0, UUID.randomUUID()))
        }

        // When
        rumAnonymousIdentifierManager.manageAnonymousId(true)

        // Then
        verify(dataStore, never()).setValue(eq("anonymous_id_key"), any(), eq(0), isNull(), any())
        verify(dataStore, never()).removeValue(eq("anonymous_id_key"), isNull())
        verify(core).setAnonymousId(any())
    }

    @Test
    fun `M do not generate new anonymous id W tracking disabled`() {
        // Given
        val shouldTrack = false

        // When
        rumAnonymousIdentifierManager.manageAnonymousId(shouldTrack)

        // Then
        verify(dataStore).removeValue(eq("anonymous_id_key"), isNull())
        verify(core).setAnonymousId(isNull())
    }
}
