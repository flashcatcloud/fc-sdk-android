/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.tools.unit

import android.annotation.SuppressLint
import java.lang.reflect.Field
import java.lang.reflect.Modifier

internal object RemoveFinalModifier {
    @SuppressLint("DiscouragedPrivateApi")
    fun remove(field: Field) {
        Field::class.java.getDeclaredField("accessFlags")
            .apply {
                isAccessible = true
            }
            .set(field, field.modifiers and Modifier.FINAL.inv())
    }
}
