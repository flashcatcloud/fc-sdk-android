/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package com.datadog.android.utils.forge

import com.flashcat.android.core.internal.persistence.BatchData
import com.flashcat.android.core.internal.persistence.BatchId
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class BatchDataForgeryFactory : ForgeryFactory<BatchData> {
    override fun getForgery(forge: Forge): BatchData {
        return BatchData(
            id = BatchId(id = forge.anAlphaNumericalString()),
            data = forge.aList { getForgery() },
            metadata = forge.aNullable {
                forge.aString(
                    forge.anInt(min = 0, max = DATA_SIZE_LIMIT)
                ).toByteArray()
            }
        )
    }
    companion object {
        const val DATA_SIZE_LIMIT = 32
    }
}
