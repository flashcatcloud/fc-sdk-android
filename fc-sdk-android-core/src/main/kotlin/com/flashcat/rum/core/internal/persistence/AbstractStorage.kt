/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.core.internal.persistence

import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import com.flashcat.rum.api.InternalLogger
import com.flashcat.rum.api.context.FlashcatContext
import com.flashcat.rum.api.feature.EventWriteScope
import com.flashcat.rum.api.storage.EventBatchWriter
import com.flashcat.rum.api.storage.EventType
import com.flashcat.rum.api.storage.FeatureStorageConfiguration
import com.flashcat.rum.api.storage.RawBatchEvent
import com.flashcat.rum.core.internal.metrics.RemovalReason
import com.flashcat.rum.core.internal.privacy.ConsentProvider
import com.flashcat.rum.core.internal.utils.executeSafe
import com.flashcat.rum.core.persistence.NoOpPersistenceStrategy
import com.flashcat.rum.core.persistence.PersistenceStrategy
import com.flashcat.rum.privacy.TrackingConsent
import com.flashcat.rum.privacy.TrackingConsentProviderCallback
import java.util.concurrent.ExecutorService

internal class AbstractStorage(
    internal val sdkCoreId: String?,
    private val featureName: String,
    internal val persistenceStrategyFactory: PersistenceStrategy.Factory,
    private val executorService: ExecutorService,
    private val internalLogger: InternalLogger,
    internal val storageConfiguration: FeatureStorageConfiguration,
    consentProvider: ConsentProvider
) : Storage, TrackingConsentProviderCallback {

    private val grantedPersistenceStrategy: PersistenceStrategy by lazy {
        persistenceStrategyFactory.create(
            "$sdkCoreId/$featureName/${TrackingConsent.GRANTED}",
            storageConfiguration.maxItemsPerBatch,
            storageConfiguration.maxBatchSize
        )
    }

    private val pendingPersistenceStrategy: PersistenceStrategy by lazy {
        persistenceStrategyFactory.create(
            "$sdkCoreId/$featureName/${TrackingConsent.PENDING}",
            storageConfiguration.maxItemsPerBatch,
            storageConfiguration.maxBatchSize
        )
    }

    private val writeLock = Any()

    private val notGrantedPersistenceStrategy: PersistenceStrategy = NoOpPersistenceStrategy()

    init {
        @Suppress("LeakingThis")
        consentProvider.registerCallback(this)
    }

    // region Storage

    @AnyThread
    override fun getEventWriteScope(
        flashcatContext: FlashcatContext
    ): EventWriteScope {
        val strategy = resolvePersistenceStrategy(flashcatContext)
        val writer = object : EventBatchWriter {
            @WorkerThread
            override fun currentMetadata(): ByteArray? {
                return strategy.currentMetadata()
            }

            @WorkerThread
            override fun write(event: RawBatchEvent, batchMetadata: ByteArray?, eventType: EventType): Boolean {
                return strategy.write(event, batchMetadata, eventType)
            }
        }
        // although we don't know what storage is backed by the persistence strategy, so maybe writing in a concurrent
        // way is fine there and lock is not needed, but taking precautions
        return AsyncEventWriteScope(executorService, writer, writeLock, featureName, internalLogger)
    }

    private fun resolvePersistenceStrategy(flashcatContext: FlashcatContext) =
        when (flashcatContext.trackingConsent) {
            TrackingConsent.GRANTED -> grantedPersistenceStrategy
            TrackingConsent.PENDING -> pendingPersistenceStrategy
            TrackingConsent.NOT_GRANTED -> notGrantedPersistenceStrategy
        }

    @WorkerThread
    override fun readNextBatch(): BatchData? {
        return grantedPersistenceStrategy.lockAndReadNext()?.let {
            BatchData(
                id = BatchId(it.batchId),
                data = it.events,
                metadata = it.metadata
            )
        }
    }

    @WorkerThread
    override fun confirmBatchRead(
        batchId: BatchId,
        removalReason: RemovalReason,
        deleteBatch: Boolean
    ) {
        if (deleteBatch) {
            grantedPersistenceStrategy.unlockAndDelete(batchId.id)
        } else {
            grantedPersistenceStrategy.unlockAndKeep(batchId.id)
        }
    }

    @AnyThread
    override fun dropAll() {
        executorService.executeSafe("Data drop", internalLogger) {
            grantedPersistenceStrategy.dropAll()
            pendingPersistenceStrategy.dropAll()
        }
    }

    // endregion

    // region TrackingConsentProviderCallback

    override fun onConsentUpdated(
        previousConsent: TrackingConsent,
        newConsent: TrackingConsent
    ) {
        executorService.executeSafe("Data migration", internalLogger) {
            if (previousConsent == TrackingConsent.PENDING) {
                when (newConsent) {
                    TrackingConsent.GRANTED -> pendingPersistenceStrategy.migrateData(grantedPersistenceStrategy)
                    TrackingConsent.NOT_GRANTED -> pendingPersistenceStrategy.dropAll()
                    TrackingConsent.PENDING -> {
                        // Nothing to do
                    }
                }
            }
        }
    }

    // endregion
}
