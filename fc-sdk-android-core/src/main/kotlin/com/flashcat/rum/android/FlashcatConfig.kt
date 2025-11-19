/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * 
 * Modifications (c) 2025 Flashcat (Beijing) Technology Co., Ltd.
 * This file has been created by Flashcat for use with the Flashcat RUM platform.
 */

package com.flashcat.rum.android

/**
 * An object describing the configuration of the Flashcat SDK.
 *
 * This is necessary to initialize the SDK with the [FlashcatRum.initialize] method.
 */
class FlashcatConfig private constructor(
    internal val clientToken: String,
    internal val env: String,
    internal val variant: String,
    internal val service: String?,
    internal val site: FlashcatSite,
    internal val customEndpoint: String?,
    internal val crashReportsEnabled: Boolean,
    internal val developerModeEnabled: Boolean,
    internal val firstPartyHosts: Map<String, Set<String>>?,
    internal val batchSize: BatchSize?,
    internal val uploadFrequency: UploadFrequency?,
    internal val batchProcessingLevel: BatchProcessingLevel?,
    internal val additionalConfig: Map<String, Any>,
    internal val proxy: ProxyConfig?,
    internal val encryption: EncryptionConfig?,
    internal val persistenceStrategyFactory: Any?
) {

    /**
     * Batch size configuration.
     */
    enum class BatchSize {
        SMALL, MEDIUM, LARGE
    }

    /**
     * Upload frequency configuration.
     */
    enum class UploadFrequency {
        FREQUENT, AVERAGE, RARE
    }

    /**
     * Batch processing level configuration.
     */
    enum class BatchProcessingLevel {
        LOW, MEDIUM, HIGH
    }

    /**
     * Proxy configuration.
     */
    data class ProxyConfig(
        val proxy: java.net.Proxy,
        val authenticator: okhttp3.Authenticator?
    )

    /**
     * Encryption configuration.
     */
    interface EncryptionConfig {
        fun encrypt(data: ByteArray): ByteArray
        fun decrypt(data: ByteArray): ByteArray
    }

    /**
     * A Builder class for a [FlashcatConfig].
     *
     * @param clientToken your API key of type Client Token
     * @param env the environment name that will be sent with each event. This can be used to
     * filter your events on different environments (e.g.: "staging" vs. "production").
     * @param variant the variant of your application, which should be the value from your
     * `BuildConfig.FLAVOR` constant if you have different flavors, empty string otherwise.
     * @param service the service name (if set to null, it'll be set to your application's
     * package name, e.g.: com.example.android)
     */
    @Suppress("TooManyFunctions")
    class Builder
    @JvmOverloads
    constructor(
        private val clientToken: String,
        private val env: String,
        private val variant: String = "",
        private val service: String? = null
    ) {
        private var site: FlashcatSite = FlashcatSite.PRODUCTION
        private var customEndpoint: String? = null
        private var crashReportsEnabled: Boolean = true
        private var developerModeEnabled: Boolean = false
        private var firstPartyHosts: Map<String, Set<String>>? = null
        private var batchSize: BatchSize? = null
        private var uploadFrequency: UploadFrequency? = null
        private var batchProcessingLevel: BatchProcessingLevel? = null
        private var additionalConfig: Map<String, Any> = emptyMap()
        private var proxy: ProxyConfig? = null
        private var encryption: EncryptionConfig? = null
        private var persistenceStrategyFactory: Any? = null

        /**
         * Builds a [FlashcatConfig] based on the current state of this Builder.
         */
        fun build(): FlashcatConfig {
            return FlashcatConfig(
                clientToken = clientToken,
                env = env,
                variant = variant,
                service = service,
                site = site,
                customEndpoint = customEndpoint,
                crashReportsEnabled = crashReportsEnabled,
                developerModeEnabled = developerModeEnabled,
                firstPartyHosts = firstPartyHosts,
                batchSize = batchSize,
                uploadFrequency = uploadFrequency,
                batchProcessingLevel = batchProcessingLevel,
                additionalConfig = additionalConfig,
                proxy = proxy,
                encryption = encryption,
                persistenceStrategyFactory = persistenceStrategyFactory
            )
        }

        /**
         * Sets the Flashcat site for data uploads.
         * Default is [FlashcatSite.PRODUCTION].
         *
         * @param site the Flashcat site to use
         * @return this Builder instance
         */
        fun useSite(site: FlashcatSite): Builder {
            this.site = site
            return this
        }

        /**
         * Sets a custom endpoint URL for data uploads.
         * This overrides the site configuration and allows you to send data to a custom server.
         *
         * Useful for:
         * - Internal testing (e.g., staging environment)
         * - Private deployment
         * - Local development
         *
         * @param endpointUrl the custom endpoint base URL (e.g., "https://jira.flashcat.cloud")
         * @return this Builder instance
         *
         * @see FlashcatSite
         */
        fun useCustomEndpoint(endpointUrl: String): Builder {
            this.customEndpoint = endpointUrl
            return this
        }

        /**
         * Sets the SDK to be more verbose when an application is set to `debuggable`.
         * @param developerModeEnabled Enable or disable extra debug info when an app is debuggable
         * @return this Builder instance
         */
        fun setUseDeveloperModeWhenDebuggable(developerModeEnabled: Boolean): Builder {
            this.developerModeEnabled = developerModeEnabled
            return this
        }

        /**
         * Defines the list of first party hosts.
         * Requests made to these hosts will have tracing information injected automatically.
         *
         * @param hosts a list of all the hosts that you own (or your main application domain).
         * @param tracingHeaderTypes the set of tracing header type names ("DATADOG", "TRACECONTEXT", "B3", "B3MULTI")
         * @return this Builder instance
         */
        fun setFirstPartyHosts(
            hosts: List<String>,
            tracingHeaderTypes: Set<String> = setOf("DATADOG")
        ): Builder {
            this.firstPartyHosts = hosts.associateWith { tracingHeaderTypes }
            return this
        }

        /**
         * Defines the batch size for uploads.
         * @param batchSize the batch size
         * @return this Builder instance
         */
        fun setBatchSize(batchSize: BatchSize): Builder {
            this.batchSize = batchSize
            return this
        }

        /**
         * Defines the upload frequency for batches.
         * @param uploadFrequency the upload frequency
         * @return this Builder instance
         */
        fun setUploadFrequency(uploadFrequency: UploadFrequency): Builder {
            this.uploadFrequency = uploadFrequency
            return this
        }

        /**
         * Sets the batch processing level.
         * @param level the batch processing level
         * @return this Builder instance
         */
        fun setBatchProcessingLevel(level: BatchProcessingLevel): Builder {
            this.batchProcessingLevel = level
            return this
        }

        /**
         * Enables/disables crash reports.
         * @param enabled whether crash reports should be sent
         * @return this Builder instance
         */
        fun setCrashReportsEnabled(enabled: Boolean): Builder {
            this.crashReportsEnabled = enabled
            return this
        }

        /**
         * Allows to provide additional configuration values which can be used by the SDK.
         * @param additionalConfig Additional configuration values.
         * @return this Builder instance
         */
        fun setAdditionalConfiguration(additionalConfig: Map<String, Any>): Builder {
            this.additionalConfig = additionalConfig
            return this
        }

        /**
         * Enables a custom proxy for uploading tracked data to Flashcat's intake.
         * @param proxy the [java.net.Proxy] configuration
         * @param authenticator the optional [okhttp3.Authenticator] for the proxy
         * @return this Builder instance
         */
        fun setProxy(proxy: java.net.Proxy, authenticator: okhttp3.Authenticator? = null): Builder {
            this.proxy = ProxyConfig(proxy, authenticator)
            return this
        }

        /**
         * Allows to set the encryption for the local data. By default no encryption is used.
         *
         * @param dataEncryption An encryption object implementing [EncryptionConfig] interface.
         * @return this Builder instance
         */
        fun setEncryption(dataEncryption: EncryptionConfig): Builder {
            this.encryption = dataEncryption
            return this
        }

        /**
         * Allows to use a custom persistence strategy.
         * @param persistenceStrategyFactory the persistence strategy to use (or null to use the default one)
         * @return this Builder instance
         */
        fun setPersistenceStrategyFactory(persistenceStrategyFactory: Any?): Builder {
            this.persistenceStrategyFactory = persistenceStrategyFactory
            return this
        }
    }
}

