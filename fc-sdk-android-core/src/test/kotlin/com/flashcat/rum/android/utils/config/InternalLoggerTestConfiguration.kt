/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.utils.config

import com.flashcat.rum.api.InternalLogger
import com.flashcat.rum.core.internal.utils.unboundInternalLogger
import com.flashcat.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.Forge
import org.mockito.kotlin.mock

internal class InternalLoggerTestConfiguration : TestConfiguration {

    lateinit var mockInternalLogger: InternalLogger

    private lateinit var originalInternalLogger: InternalLogger

    override fun setUp(forge: Forge) {
        mockInternalLogger = mock()

        originalInternalLogger = unboundInternalLogger

        unboundInternalLogger = mockInternalLogger
    }

    override fun tearDown(forge: Forge) {
        unboundInternalLogger = originalInternalLogger
    }
}
