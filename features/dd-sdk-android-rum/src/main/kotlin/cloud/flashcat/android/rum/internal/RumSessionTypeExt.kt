/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.rum.internal

import cloud.flashcat.android.rum.RumSessionType
import cloud.flashcat.android.rum.model.ActionEvent
import cloud.flashcat.android.rum.model.ErrorEvent
import cloud.flashcat.android.rum.model.LongTaskEvent
import cloud.flashcat.android.rum.model.ResourceEvent
import cloud.flashcat.android.rum.model.RumVitalOperationStepEvent
import cloud.flashcat.android.rum.model.ViewEvent

internal fun RumSessionType.toAction(): ActionEvent.ActionEventSessionType {
    return when (this) {
        RumSessionType.SYNTHETICS -> ActionEvent.ActionEventSessionType.SYNTHETICS
        RumSessionType.USER -> ActionEvent.ActionEventSessionType.USER
    }
}

internal fun RumSessionType.toResource(): ResourceEvent.ResourceEventSessionType {
    return when (this) {
        RumSessionType.SYNTHETICS -> ResourceEvent.ResourceEventSessionType.SYNTHETICS
        RumSessionType.USER -> ResourceEvent.ResourceEventSessionType.USER
    }
}

internal fun RumSessionType.toError(): ErrorEvent.ErrorEventSessionType {
    return when (this) {
        RumSessionType.SYNTHETICS -> ErrorEvent.ErrorEventSessionType.SYNTHETICS
        RumSessionType.USER -> ErrorEvent.ErrorEventSessionType.USER
    }
}

internal fun RumSessionType.toView(): ViewEvent.ViewEventSessionType {
    return when (this) {
        RumSessionType.SYNTHETICS -> ViewEvent.ViewEventSessionType.SYNTHETICS
        RumSessionType.USER -> ViewEvent.ViewEventSessionType.USER
    }
}

internal fun RumSessionType.toLongTask(): LongTaskEvent.LongTaskEventSessionType {
    return when (this) {
        RumSessionType.SYNTHETICS -> LongTaskEvent.LongTaskEventSessionType.SYNTHETICS
        RumSessionType.USER -> LongTaskEvent.LongTaskEventSessionType.USER
    }
}
internal fun RumSessionType.toVital(): RumVitalOperationStepEvent.RumVitalOperationStepEventSessionType {
    return when (this) {
        RumSessionType.SYNTHETICS -> RumVitalOperationStepEvent.RumVitalOperationStepEventSessionType.SYNTHETICS
        RumSessionType.USER -> RumVitalOperationStepEvent.RumVitalOperationStepEventSessionType.USER
    }
}
