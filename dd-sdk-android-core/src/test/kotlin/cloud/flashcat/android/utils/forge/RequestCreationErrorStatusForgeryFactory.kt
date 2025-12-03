/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.utils.forge

import cloud.flashcat.android.core.internal.data.upload.UploadStatus
import cloud.flashcat.tools.unit.forge.anException
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class RequestCreationErrorStatusForgeryFactory : ForgeryFactory<UploadStatus.RequestCreationError> {

    override fun getForgery(forge: Forge): UploadStatus.RequestCreationError {
        return UploadStatus.RequestCreationError(forge.anException())
    }
}
