/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.core.integration.tests.forge.factories

import com.flashcat.rum.FlashcatSite
import com.flashcat.rum._InternalProxy
import com.flashcat.rum.core.configuration.BatchProcessingLevel
import com.flashcat.rum.core.configuration.BatchSize
import com.flashcat.rum.core.configuration.Configuration
import com.flashcat.rum.core.configuration.UploadFrequency
import com.flashcat.rum.trace.TracingHeaderType
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import java.util.UUID

internal class ConfigurationCoreForgeryFactory :
    ForgeryFactory<Configuration> {
    override fun getForgery(forge: Forge): Configuration {
        return Configuration.Builder(
            UUID.randomUUID().toString(),
            forge.anHexadecimalString(),
            forge.anHexadecimalString(),
            forge.aNullable {
                anAlphaNumericalString()
            }
        )
            .setUseDeveloperModeWhenDebuggable(forge.aBool())
            // this needs to be before allowing the clear text traffic as it invalidates this option
            .useSite(forge.aValueFrom(FlashcatSite::class.java))
            .setFirstPartyHostsWithHeaderType(
                forge.aMap {
                    val fakeUrl = forge.aStringMatching("https://[a-z0-9]+\\.com")
                    fakeUrl to aList {
                        aValueFrom(
                            TracingHeaderType::class.java
                        )
                    }.toSet()
                }
            )
            .apply {
                _InternalProxy.allowClearTextHttp(this)
            }
            .setBatchSize(BatchSize.SMALL)
            .setUploadFrequency(UploadFrequency.FREQUENT)
            .setBatchProcessingLevel(BatchProcessingLevel.HIGH)
            .build()
    }
}
