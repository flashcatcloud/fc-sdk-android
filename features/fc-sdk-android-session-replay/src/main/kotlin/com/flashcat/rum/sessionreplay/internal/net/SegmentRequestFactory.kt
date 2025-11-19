/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sessionreplay.internal.net

import com.flashcat.rum.api.context.FlashcatContext
import com.flashcat.rum.api.net.Request
import com.flashcat.rum.api.net.RequestExecutionContext
import com.flashcat.rum.api.net.RequestFactory
import com.flashcat.rum.api.storage.RawBatchEvent
import com.flashcat.rum.sessionreplay.internal.exception.InvalidPayloadFormatException
import okhttp3.RequestBody
import okio.Buffer
import java.util.UUID

internal class SegmentRequestFactory(
    internal val customEndpointUrl: String?,
    private val batchToSegmentsMapper: BatchesToSegmentsMapper,
    private val segmentRequestBodyFactory: SegmentRequestBodyFactory = SegmentRequestBodyFactory()
) : RequestFactory {

    override fun create(
        context: FlashcatContext,
        executionContext: RequestExecutionContext,
        batchData: List<RawBatchEvent>,
        batchMetadata: ByteArray?
    ): Request {
        val serializedSegmentPair = batchToSegmentsMapper.map(context, batchData.map { it.data })
        if (serializedSegmentPair.isEmpty()) {
            @Suppress("ThrowingInternalException")
            throw InvalidPayloadFormatException(
                "The payload format was broken and an upload" +
                    " request could not be created"
            )
        }
        val body = segmentRequestBodyFactory.create(serializedSegmentPair)
        return resolveRequest(context, body)
    }

    private fun buildUrl(flashcatContext: FlashcatContext): String {
        return customEndpointUrl ?: (flashcatContext.site.intakeEndpoint + "/api/v2/replay")
    }

    private fun resolveHeaders(flashcatContext: FlashcatContext, requestId: String): Map<String, String> {
        return mapOf(
            RequestFactory.HEADER_API_KEY to flashcatContext.clientToken,
            RequestFactory.HEADER_EVP_ORIGIN to flashcatContext.source,
            RequestFactory.HEADER_EVP_ORIGIN_VERSION to flashcatContext.sdkVersion,
            RequestFactory.HEADER_REQUEST_ID to requestId
        )
    }

    @Suppress("ReturnCount")
    private fun resolveRequest(context: FlashcatContext, body: RequestBody): Request {
        val bodyAsByteArray = extractByteArrayFromBody(body)
        val requestId = UUID.randomUUID().toString()
        val description = "Session Replay Segment Upload Request"
        val headers = resolveHeaders(context, requestId)
        val requestUrl = buildUrl(context)
        return Request(
            requestId,
            description,
            requestUrl,
            headers,
            body = bodyAsByteArray,
            contentType = body.contentType().toString()
        )
    }

    private fun extractByteArrayFromBody(body: RequestBody): ByteArray {
        val buffer = Buffer()
        @Suppress("UnsafeThirdPartyFunctionCall")
        body.writeTo(buffer)
        @Suppress("UnsafeThirdPartyFunctionCall")
        return buffer.readByteArray()
    }

    // endregion
}
