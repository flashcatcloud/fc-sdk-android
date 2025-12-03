/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.trace.internal.net

import cloud.flashcat.android.api.InternalLogger
import cloud.flashcat.android.api.context.DatadogContext
import cloud.flashcat.android.api.net.Request
import cloud.flashcat.android.api.net.RequestExecutionContext
import cloud.flashcat.android.api.net.RequestFactory
import cloud.flashcat.android.api.storage.RawBatchEvent
import cloud.flashcat.android.core.internal.utils.join
import java.util.UUID

internal class TracesRequestFactory(
    internal val customEndpointUrl: String?,
    private val internalLogger: InternalLogger
) : RequestFactory {

    override fun create(
        context: DatadogContext,
        executionContext: RequestExecutionContext,
        batchData: List<RawBatchEvent>,
        batchMetadata: ByteArray?
    ): Request? {
        val requestId = UUID.randomUUID().toString()

        val baseUrl = customEndpointUrl ?: (context.site.intakeEndpoint + "/api/v2/spans")
        return Request(
            id = requestId,
            description = "Traces Request",
            url = baseUrl,
            headers = buildHeaders(
                requestId,
                context.clientToken,
                context.source,
                context.sdkVersion
            ),
            body = batchData.map { it.data }
                .join(
                    separator = PAYLOAD_SEPARATOR,
                    internalLogger = internalLogger
                ),
            contentType = RequestFactory.CONTENT_TYPE_TEXT_UTF8
        )
    }

    private fun buildHeaders(
        requestId: String,
        clientToken: String,
        source: String,
        sdkVersion: String
    ): Map<String, String> {
        return mapOf(
            RequestFactory.HEADER_API_KEY to clientToken,
            RequestFactory.HEADER_EVP_ORIGIN to source,
            RequestFactory.HEADER_EVP_ORIGIN_VERSION to sdkVersion,
            RequestFactory.HEADER_REQUEST_ID to requestId
        )
    }

    companion object {
        private val PAYLOAD_SEPARATOR = "\n".toByteArray(Charsets.UTF_8)
    }
}
