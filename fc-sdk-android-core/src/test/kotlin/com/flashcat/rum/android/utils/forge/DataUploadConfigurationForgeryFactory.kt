/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.utils.forge

import com.flashcat.rum.core.configuration.UploadFrequency
import com.flashcat.rum.core.internal.configuration.DataUploadConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class DataUploadConfigurationForgeryFactory : ForgeryFactory<DataUploadConfiguration> {
    override fun getForgery(forge: Forge): DataUploadConfiguration {
        val frequency: UploadFrequency = forge.getForgery()
        // we limit the size to avoid OOM errors inside our tests
        return DataUploadConfiguration(frequency, forge.anInt(min = 1, max = 200))
    }
}
