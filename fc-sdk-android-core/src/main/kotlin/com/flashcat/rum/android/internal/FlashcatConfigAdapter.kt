/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * 
 * Modifications (c) 2025 Flashcat (Beijing) Technology Co., Ltd.
 * This file has been created by Flashcat for use with the Flashcat RUM platform.
 */

package com.flashcat.rum.android.internal

import com.datadog.android.DatadogSite
import com.datadog.android.core.configuration.BatchProcessingLevel
import com.datadog.android.core.configuration.BatchSize
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.UploadFrequency
import com.datadog.android.core.persistence.PersistenceStrategy
import com.datadog.android.security.Encryption
import com.datadog.android.trace.TracingHeaderType
import com.flashcat.rum.android.FlashcatConfig
import com.flashcat.rum.android.FlashcatSite

/**
 * Internal adapter to convert FlashcatConfig to Datadog Configuration.
 * 
 * This class is internal and should not be used by SDK consumers.
 */
internal object FlashcatConfigAdapter {

    /**
     * Converts a FlashcatConfig to a Datadog Configuration.
     */
    fun toDatadogConfiguration(flashcatConfig: FlashcatConfig): Configuration {
        val configBuilder = Configuration.Builder(
            clientToken = flashcatConfig.clientToken,
            env = flashcatConfig.env,
            variant = flashcatConfig.variant,
            service = flashcatConfig.service
        )

        // Map FlashcatSite to DatadogSite
        // We use US1 as a placeholder, and the actual endpoint will be handled by the Feature's RequestFactory
        // which should be updated to respect FlashcatSite configuration
        val datadogSite = DatadogSite.US1
        configBuilder.useSite(datadogSite)

        // Apply custom endpoint if specified
        if (flashcatConfig.customEndpoint != null) {
            // This is a workaround as we can't easily override the site in Configuration
            // The actual endpoint override logic needs to be implemented in RequestFactory
        }

        // Apply developer mode
        if (flashcatConfig.developerModeEnabled) {
            configBuilder.setUseDeveloperModeWhenDebuggable(true)
        }

        // Apply first party hosts
        flashcatConfig.firstPartyHosts?.let { hostsMap ->
            hostsMap.forEach { (host, headerTypeNames) ->
                val headerTypes = headerTypeNames.mapNotNull { name ->
                    when (name.uppercase()) {
                        "DATADOG" -> TracingHeaderType.DATADOG
                        "TRACECONTEXT" -> TracingHeaderType.TRACECONTEXT
                        "B3" -> TracingHeaderType.B3
                        "B3MULTI" -> TracingHeaderType.B3MULTI
                        else -> null
                    }
                }.toSet()
                if (headerTypes.isNotEmpty()) {
                    configBuilder.setFirstPartyHosts(listOf(host), headerTypes)
                }
            }
        }

        // Apply batch size
        flashcatConfig.batchSize?.let {
            val datadogBatchSize = when (it) {
                FlashcatConfig.BatchSize.SMALL -> BatchSize.SMALL
                FlashcatConfig.BatchSize.MEDIUM -> BatchSize.MEDIUM
                FlashcatConfig.BatchSize.LARGE -> BatchSize.LARGE
            }
            configBuilder.setBatchSize(datadogBatchSize)
        }

        // Apply upload frequency
        flashcatConfig.uploadFrequency?.let {
            val datadogUploadFrequency = when (it) {
                FlashcatConfig.UploadFrequency.FREQUENT -> UploadFrequency.FREQUENT
                FlashcatConfig.UploadFrequency.AVERAGE -> UploadFrequency.AVERAGE
                FlashcatConfig.UploadFrequency.RARE -> UploadFrequency.RARE
            }
            configBuilder.setUploadFrequency(datadogUploadFrequency)
        }

        // Apply batch processing level
        flashcatConfig.batchProcessingLevel?.let {
            val datadogBatchProcessingLevel = when (it) {
                FlashcatConfig.BatchProcessingLevel.LOW -> BatchProcessingLevel.LOW
                FlashcatConfig.BatchProcessingLevel.MEDIUM -> BatchProcessingLevel.MEDIUM
                FlashcatConfig.BatchProcessingLevel.HIGH -> BatchProcessingLevel.HIGH
            }
            configBuilder.setBatchProcessingLevel(datadogBatchProcessingLevel)
        }

        // Apply crash reports
        configBuilder.setCrashReportsEnabled(flashcatConfig.crashReportsEnabled)

        // Apply additional config
        if (flashcatConfig.additionalConfig.isNotEmpty()) {
            configBuilder.setAdditionalConfiguration(flashcatConfig.additionalConfig)
        }

        // Apply proxy
        flashcatConfig.proxy?.let { proxyConfig ->
            configBuilder.setProxy(proxyConfig.proxy, proxyConfig.authenticator)
        }

        // Apply encryption
        flashcatConfig.encryption?.let { encryptionConfig ->
            val datadogEncryption = object : Encryption {
                override fun encrypt(data: ByteArray): ByteArray = encryptionConfig.encrypt(data)
                override fun decrypt(data: ByteArray): ByteArray = encryptionConfig.decrypt(data)
            }
            configBuilder.setEncryption(datadogEncryption)
        }

        // Apply persistence strategy factory
        flashcatConfig.persistenceStrategyFactory?.let {
            if (it is PersistenceStrategy.Factory) {
                configBuilder.setPersistenceStrategyFactory(it)
            }
        }

        return configBuilder.build()
    }

}
