/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.utils.forge

import cloud.flashcat.android.internal.tests.elmyr.InternalTelemetryApiUsageForgeryFactory
import cloud.flashcat.android.internal.tests.elmyr.TracingHeaderTypesSetForgeryFactory
import cloud.flashcat.android.test.elmyr.PersistenceStrategyBatchForgeryFactory
import cloud.flashcat.android.tests.elmyr.useCoreFactories
import cloud.flashcat.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.jvm.useJvmFactories

internal class Configurator :
    BaseConfigurator() {
    override fun configure(forge: Forge) {
        super.configure(forge)
        forge.useCoreFactories()

        // Datadog Core
        forge.addFactory(CustomAttributesForgeryFactory())
        forge.addFactory(ConfigurationForgeryFactory())
        forge.addFactory(ConfigurationCoreForgeryFactory())
        forge.addFactory(FilePersistenceConfigForgeryFactory())
        forge.addFactory(AndroidInfoProviderForgeryFactory())
        forge.addFactory(FeatureStorageConfigurationForgeryFactory())
        forge.addFactory(BatchDataForgeryFactory())
        forge.addFactory(BatchIdForgeryFactory())

        // IO
        forge.addFactory(BatchForgeryFactory())
        forge.addFactory(PayloadDecorationForgeryFactory())
        forge.addFactory(WorkerParametersForgeryFactory())

        // NDK Crash
        forge.addFactory(NdkCrashLogForgeryFactory())

        // MISC
        forge.addFactory(BigIntegerFactory())
        forge.addFactory(CharsetForgeryFactory())

        // Datadog SDK v2
        forge.addFactory(DataUploadConfigurationForgeryFactory())

        // UploadStatus
        forge.addFactory(DNSErrorStatusForgeryFactory())
        forge.addFactory(HttpClientErrorForgeryFactory())
        forge.addFactory(HttpClientRateLimitingStatusForgeryFactory())
        forge.addFactory(HttpRedirectStatusForgeryFactory())
        forge.addFactory(HttpServerErrorForgeryFactory())
        forge.addFactory(InvalidTokenErrorStatusForgeryFactory())
        forge.addFactory(NetworkErrorStatusForgeryFactory())
        forge.addFactory(RequestCreationErrorStatusForgeryFactory())
        forge.addFactory(SuccessStatusForgeryFactory())
        forge.addFactory(UnknownExceptionStatusForgeryFactory())
        forge.addFactory(UnknownHttpErrorStatusForgeryFactory())
        forge.addFactory(UnknownStatusForgeryFactory())

        // RemovalReason
        forge.addFactory(RemovalReasonFlushedForgeryFactory())
        forge.addFactory(RemovalReasonPurgedForgeryFactory())
        forge.addFactory(RemovalReasonInvalidForgeryFactory())
        forge.addFactory(RemovalReasonObsoleteForgeryFactory())
        forge.addFactory(RemovalReasonIntakeCodeForgeryFactory())
        forge.addFactory(RemovalReasonForgeryFactory())

        forge.addFactory(BatchClosedMetadataForgeryFactory())

        forge.addFactory(PersistenceStrategyBatchForgeryFactory())

        forge.useJvmFactories()

        // telemetry
        forge.addFactory(InternalTelemetryApiUsageForgeryFactory())
        forge.addFactory(TracingHeaderTypesSetForgeryFactory())
    }
}
