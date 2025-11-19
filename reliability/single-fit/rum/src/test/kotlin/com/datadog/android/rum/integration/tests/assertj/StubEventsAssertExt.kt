/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.rum.integration.tests.assertj

import com.flashcat.rum.tests.assertj.StubEventsAssert

fun StubEventsAssert.hasRumEvent(index: Int, assertion: RumEventAssert.() -> Unit): StubEventsAssert {
    hasJsonObject(index) {
        val rumEventAssert = RumEventAssert(it)
        rumEventAssert.assertion()
    }
    return this
}
