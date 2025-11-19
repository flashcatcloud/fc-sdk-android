/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.rum.internal

import com.flashcat.rum.rum.RumSessionType
import com.flashcat.rum.rum.model.ActionEvent
import com.flashcat.rum.rum.model.ErrorEvent
import com.flashcat.rum.rum.model.LongTaskEvent
import com.flashcat.rum.rum.model.ResourceEvent
import com.flashcat.rum.rum.model.RumVitalOperationStepEvent
import com.flashcat.rum.rum.model.ViewEvent

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
