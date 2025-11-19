/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.utils.forge

import com.flashcat.rum.api.storage.FeatureStorageConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class FeatureStorageConfigurationForgeryFactory :
    ForgeryFactory<FeatureStorageConfiguration> {
    override fun getForgery(forge: Forge): FeatureStorageConfiguration {
        return FeatureStorageConfiguration(
            maxBatchSize = forge.aPositiveLong(),
            maxItemsPerBatch = forge.aBigInt(),
            maxItemSize = forge.aPositiveLong(),
            oldBatchThreshold = forge.aPositiveLong()
        )
    }
}
