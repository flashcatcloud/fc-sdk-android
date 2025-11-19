/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.log.internal.net

import com.flashcat.rum.api.InternalLogger
import com.flashcat.rum.api.context.FlashcatContext
import com.flashcat.rum.api.net.RequestExecutionContext
import com.flashcat.rum.api.net.RequestFactory
import com.flashcat.rum.api.storage.RawBatchEvent
import com.flashcat.rum.core.internal.utils.join
import com.flashcat.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class LogsRequestFactoryTest {

    private lateinit var testedFactory: LogsRequestFactory

    @Forgery
    lateinit var fakeFlashcatContext: FlashcatContext

    @BeforeEach
    fun `set up`() {
        testedFactory = LogsRequestFactory(
            customEndpointUrl = null,
            internalLogger = InternalLogger.UNBOUND
        )
    }

    @Suppress("NAME_SHADOWING")
    @Test
    fun `M create a proper request W create()`(
        @Forgery batchData: List<RawBatchEvent>,
        @Forgery executionContext: RequestExecutionContext,
        @StringForgery batchMetadata: String,
        forge: Forge
    ) {
        // Given
        val batchMetadata = forge.aNullable { batchMetadata.toByteArray() }

        // When
        val request = testedFactory.create(fakeFlashcatContext, executionContext, batchData, batchMetadata)

        // Then
        requireNotNull(request)
        assertThat(request.url).isEqualTo(
            "${fakeFlashcatContext.site.intakeEndpoint}/api/v2/logs?" +
                "ddsource=${fakeFlashcatContext.source}"
        )
        assertThat(request.contentType).isEqualTo(RequestFactory.CONTENT_TYPE_JSON)
        assertThat(request.headers.minus(RequestFactory.HEADER_REQUEST_ID)).isEqualTo(
            mapOf(
                RequestFactory.HEADER_API_KEY to fakeFlashcatContext.clientToken,
                RequestFactory.HEADER_EVP_ORIGIN to fakeFlashcatContext.source,
                RequestFactory.HEADER_EVP_ORIGIN_VERSION to fakeFlashcatContext.sdkVersion
            )
        )
        assertThat(request.headers[RequestFactory.HEADER_REQUEST_ID]).isNotEmpty()
        assertThat(request.id).isEqualTo(request.headers[RequestFactory.HEADER_REQUEST_ID])
        assertThat(request.description).isEqualTo("Logs Request")
        assertThat(request.body).isEqualTo(
            batchData.map { it.data }
                .join(
                    separator = ",".toByteArray(),
                    prefix = "[".toByteArray(),
                    suffix = "]".toByteArray(),
                    internalLogger = InternalLogger.UNBOUND
                )
        )
    }

    @Suppress("NAME_SHADOWING")
    @Test
    fun `M create a proper request W create() { custom endpoint }`(
        @StringForgery(regex = "https://[a-z]+\\.com(/[a-z]+)+") fakeEndpoint: String,
        @Forgery batchData: List<RawBatchEvent>,
        @StringForgery batchMetadata: String,
        @Forgery executionContext: RequestExecutionContext,
        forge: Forge
    ) {
        // Given
        testedFactory = LogsRequestFactory(
            customEndpointUrl = fakeEndpoint,
            internalLogger = InternalLogger.UNBOUND
        )
        val batchMetadata = forge.aNullable { batchMetadata.toByteArray() }

        // When
        val request = testedFactory.create(fakeFlashcatContext, executionContext, batchData, batchMetadata)

        // Then
        requireNotNull(request)
        assertThat(request.url).isEqualTo("$fakeEndpoint?ddsource=${fakeFlashcatContext.source}")
        assertThat(request.contentType).isEqualTo(RequestFactory.CONTENT_TYPE_JSON)
        assertThat(request.headers.minus(RequestFactory.HEADER_REQUEST_ID)).isEqualTo(
            mapOf(
                RequestFactory.HEADER_API_KEY to fakeFlashcatContext.clientToken,
                RequestFactory.HEADER_EVP_ORIGIN to fakeFlashcatContext.source,
                RequestFactory.HEADER_EVP_ORIGIN_VERSION to fakeFlashcatContext.sdkVersion
            )
        )
        assertThat(request.headers[RequestFactory.HEADER_REQUEST_ID]).isNotEmpty()
        assertThat(request.id).isEqualTo(request.headers[RequestFactory.HEADER_REQUEST_ID])
        assertThat(request.description).isEqualTo("Logs Request")
        assertThat(request.body).isEqualTo(
            batchData.map { it.data }
                .join(
                    separator = ",".toByteArray(),
                    prefix = "[".toByteArray(),
                    suffix = "]".toByteArray(),
                    internalLogger = InternalLogger.UNBOUND
                )
        )
    }
}
