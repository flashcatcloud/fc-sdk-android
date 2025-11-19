/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.api.feature.stub

import android.content.Context
import com.flashcat.rum.api.feature.StorageBackedFeature
import com.flashcat.rum.api.net.RequestFactory
import com.flashcat.rum.api.storage.FeatureStorageConfiguration
import fr.xgouchet.elmyr.Forge

/**
 * A stub implementation of a [StorageBackedFeature] that can be used for testing.

 */
class StubStorageBackedFeature(
    private val forge: Forge,
    private val featureName: String,
    private val endpointUrl: String
) : StorageBackedFeature {
    private var wasInitialized: Boolean = false
    private var wasStopped: Boolean = false

    override val name: String get() = featureName
    override val requestFactory: RequestFactory
        get() = StubRequestFactory(forge, endpointUrl)
    override val storageConfiguration: FeatureStorageConfiguration
        get() = FeatureStorageConfiguration.DEFAULT

    override fun onInitialize(appContext: Context) {
        wasInitialized = true
    }

    override fun onStop() {
        wasStopped = true
    }

    /**
     * Checks if the feature was initialized.
     */
    fun wasInitialized(): Boolean {
        return wasInitialized
    }

    /**
     * Checks if the feature was stopped.
     */
    fun wasStopped(): Boolean {
        return wasStopped
    }
}
