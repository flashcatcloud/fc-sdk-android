/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.utils.forge

import com.flashcat.rum.core.internal.data.upload.UploadStatus
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class HttpClientErrorForgeryFactory : ForgeryFactory<UploadStatus.HttpClientError> {

    override fun getForgery(forge: Forge): UploadStatus.HttpClientError {
        return UploadStatus.HttpClientError(responseCode = forge.aPositiveInt())
    }
}
