/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.remoteconfig

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class CustomValuesTest {

    @Test
    fun `M decode plain values W decodeCustomValues()`() {
        val values = decodeCustomValues("""{"flag":true,"limit":5,"name":"beta"}""")

        assertThat(values).isEqualTo(mapOf("flag" to true, "limit" to 5, "name" to "beta"))
    }

    @Test
    fun `M decode nested objects and arrays W decodeCustomValues()`() {
        // The host application reads these directly, so a nested shape must arrive as Map and List
        // rather than as something it has to parse a second time.
        val values = decodeCustomValues("""{"viplist":["u-1","u-2"],"limits":{"rum":10}}""")

        assertThat(values).isEqualTo(
            mapOf(
                "viplist" to listOf("u-1", "u-2"),
                "limits" to mapOf("rum" to 10)
            )
        )
    }

    @Test
    fun `M decode a JSON null as null W decodeCustomValues()`() {
        val values = decodeCustomValues("""{"cleared":null}""")

        assertThat(values).containsEntry("cleared", null)
    }

    @Test
    fun `M answer nothing published W decodeCustomValues() { nothing stored }`() {
        assertThat(decodeCustomValues(null)).isNull()
    }

    @Test
    fun `M answer nothing published W decodeCustomValues() { body is not an object }`() {
        // No rate or decision depends on this bag, so an unreadable body is nothing published
        // rather than an error the application has to handle.
        assertThat(decodeCustomValues("not json")).isNull()
        assertThat(decodeCustomValues("""["an","array"]""")).isNull()
    }
}
