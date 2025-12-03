/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.rum.integration.tests.assertj

import cloud.flashcat.android.tests.assertj.StubEventsAssert

fun StubEventsAssert.hasRumEvent(index: Int, assertion: RumEventAssert.() -> Unit): StubEventsAssert {
    hasJsonObject(index) {
        val rumEventAssert = RumEventAssert(it)
        rumEventAssert.assertion()
    }
    return this
}
