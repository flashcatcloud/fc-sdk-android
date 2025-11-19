/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sessionreplay.internal.net

import com.flashcat.rum.api.InternalLogger
import com.flashcat.rum.api.context.FlashcatContext
import com.flashcat.rum.api.net.Request
import com.flashcat.rum.api.net.RequestExecutionContext
import com.flashcat.rum.api.net.RequestFactory
import com.flashcat.rum.api.storage.RawBatchEvent
import okhttp3.RequestBody
import okio.Buffer
import java.io.EOFException
import java.io.IOException
import java.util.UUID

internal class ResourcesRequestFactory(
    internal val customEndpointUrl: String?,
    private val internalLogger: InternalLogger,
    private val resourceRequestBodyFactory: ResourceRequestBodyFactory =
        ResourceRequestBodyFactory(internalLogger)
) : RequestFactory {

    @Suppress("ThrowingInternalException")
    override fun create(
        context: FlashcatContext,
        executionContext: RequestExecutionContext,
        batchData: List<RawBatchEvent>,
        batchMetadata: ByteArray?
    ): Request? {
        val requestBody = resourceRequestBodyFactory
            .create(batchData) ?: return null

        return resolveRequest(context, requestBody)
    }

    private fun resolveRequest(context: FlashcatContext, body: RequestBody): Request? {
        val bodyAsByteArray = convertBodyToByteArray(body) ?: return null
        val requestId = UUID.randomUUID().toString()
        val description = UPLOAD_DESCRIPTION
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

    private fun convertBodyToByteArray(body: RequestBody): ByteArray? {
        var result: ByteArray? = null
        val buffer = Buffer()

        try {
            body.writeTo(buffer)
        } catch (e: IOException) {
            internalLogger.log(
                level = InternalLogger.Level.ERROR,
                target = InternalLogger.Target.MAINTAINER,
                messageBuilder = { ERROR_CONVERTING_BODY_TO_BYTEARRAY },
                throwable = e
            )
        }

        try {
            result = buffer.readByteArray()
        } catch (e: EOFException) {
            internalLogger.log(
                level = InternalLogger.Level.ERROR,
                target = InternalLogger.Target.MAINTAINER,
                messageBuilder = { ERROR_CONVERTING_BODY_TO_BYTEARRAY },
                throwable = e
            )
        }

        return result
    }

    private fun resolveHeaders(flashcatContext: FlashcatContext, requestId: String): Map<String, String> {
        return mapOf(
            RequestFactory.HEADER_API_KEY to flashcatContext.clientToken,
            RequestFactory.HEADER_EVP_ORIGIN to flashcatContext.source,
            RequestFactory.HEADER_EVP_ORIGIN_VERSION to flashcatContext.sdkVersion,
            RequestFactory.HEADER_REQUEST_ID to requestId
        )
    }

    private fun buildUrl(flashcatContext: FlashcatContext): String {
        return customEndpointUrl ?: (flashcatContext.site.intakeEndpoint + "/api/v2/replay")
    }

    companion object {
        internal const val APPLICATION_ID = "application_id"
        internal const val UPLOAD_DESCRIPTION = "Session Replay Resource Upload Request"
        internal const val ERROR_CONVERTING_BODY_TO_BYTEARRAY = "Error converting request body to bytearray"
    }
}
