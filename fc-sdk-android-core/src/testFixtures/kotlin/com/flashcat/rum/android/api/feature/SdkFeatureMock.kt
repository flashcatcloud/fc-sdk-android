/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.api.feature

import com.flashcat.rum.api.context.FlashcatContext
import com.flashcat.rum.core.internal.SdkFeature
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.concurrent.Future

object SdkFeatureMock {
    /**
     * This method is a trick that allows to mock FeatureScope.getContextFuture extension method.
     */
    fun create(future: Future<FlashcatContext?>? = null): FeatureScope = mock<SdkFeature> {
        on { getContextFuture(any()) } doReturn future
    }
}
