/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.core.internal.persistence.file.advanced

import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import cloud.flashcat.android.api.InternalLogger
import cloud.flashcat.android.core.internal.persistence.file.FileOrchestrator
import cloud.flashcat.android.core.internal.persistence.file.NoOpFileOrchestrator
import cloud.flashcat.android.core.internal.privacy.ConsentProvider
import cloud.flashcat.android.core.internal.utils.executeSafe
import cloud.flashcat.android.privacy.TrackingConsent
import cloud.flashcat.android.privacy.TrackingConsentProviderCallback
import java.io.File
import java.util.concurrent.ExecutorService

internal open class ConsentAwareFileOrchestrator(
    consentProvider: ConsentProvider,
    internal val pendingOrchestrator: FileOrchestrator,
    internal val grantedOrchestrator: FileOrchestrator,
    internal val dataMigrator: DataMigrator<TrackingConsent>,
    internal val executorService: ExecutorService,
    internal val internalLogger: InternalLogger
) : FileOrchestrator, TrackingConsentProviderCallback {

    @Volatile
    private lateinit var delegateOrchestrator: FileOrchestrator

    init {
        handleConsentChange(null, consentProvider.getConsent())
        @Suppress("LeakingThis")
        consentProvider.registerCallback(this)
    }

    // region FileOrchestrator

    @WorkerThread
    override fun getWritableFile(): File? {
        return delegateOrchestrator.getWritableFile()
    }

    @WorkerThread
    override fun getReadableFile(excludeFiles: Set<File>): File? {
        return grantedOrchestrator.getReadableFile(excludeFiles)
    }

    @WorkerThread
    override fun getAllFiles(): List<File> {
        return pendingOrchestrator.getAllFiles() + grantedOrchestrator.getAllFiles()
    }

    @WorkerThread
    override fun getRootDir(): File? {
        return null
    }

    override fun getRootDirName(): String? {
        return null
    }

    @WorkerThread
    override fun getFlushableFiles(): List<File> {
        return grantedOrchestrator.getFlushableFiles()
    }

    @WorkerThread
    override fun getMetadataFile(file: File): File? {
        return delegateOrchestrator.getMetadataFile(file)
    }

    override fun decrementAndGetPendingFilesCount(): Int {
        return delegateOrchestrator.decrementAndGetPendingFilesCount()
    }

    // endregion

    // region TrackingConsentProviderCallback

    override fun onConsentUpdated(
        previousConsent: TrackingConsent,
        newConsent: TrackingConsent
    ) {
        handleConsentChange(previousConsent, newConsent)
    }

    // endregion

    // region Internal

    @AnyThread
    private fun handleConsentChange(
        previousConsent: TrackingConsent?,
        newConsent: TrackingConsent
    ) {
        val previousOrchestrator = resolveDelegateOrchestrator(previousConsent)
        val newOrchestrator = resolveDelegateOrchestrator(newConsent)
        executorService.executeSafe("Data migration", internalLogger) {
            dataMigrator.migrateData(
                previousConsent,
                previousOrchestrator,
                newConsent,
                newOrchestrator
            )
            delegateOrchestrator = newOrchestrator
        }
    }

    private fun resolveDelegateOrchestrator(consent: TrackingConsent?): FileOrchestrator {
        return when (consent) {
            TrackingConsent.PENDING, null -> pendingOrchestrator
            TrackingConsent.GRANTED -> grantedOrchestrator
            TrackingConsent.NOT_GRANTED -> NO_OP_ORCHESTRATOR
        }
    }

    // endregion

    companion object {
        internal val NO_OP_ORCHESTRATOR: FileOrchestrator = NoOpFileOrchestrator()
    }
}
