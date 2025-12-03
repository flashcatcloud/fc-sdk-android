/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.rum.metric.interactiontonextview

import cloud.flashcat.tools.annotation.NoOpImplementation

/**
 * Interface for identifying the last interaction in the previous view.
 */
@NoOpImplementation
interface LastInteractionIdentifier {
    /**
     * Validates the last interaction in the previous view.
     * @param context of the last interaction to validate
     */
    fun validate(context: PreviousViewLastInteractionContext): Boolean
}
