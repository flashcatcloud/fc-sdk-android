/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

internal class ConsumerRulesTest {

    @Test
    fun `M keep runtime dependencies W SDK checks them by reflection`() {
        val consumerRules = String(
            Files.readAllBytes(findRepositoryConsumerRules()),
            StandardCharsets.UTF_8
        )

        assertThat(consumerRules).contains("-keep class com.google.gson.** { *; }")
        assertThat(consumerRules).contains("-keep class okhttp3.OkHttpClient { *; }")
    }

    private fun findRepositoryConsumerRules(): Path {
        var current: Path? = Paths.get("").toAbsolutePath()
        while (current != null) {
            val candidate = current.resolve("consumer-rules.pro")
            if (Files.isRegularFile(candidate)) {
                return candidate
            }
            current = current.parent
        }
        error("Cannot find repository consumer-rules.pro from ${Paths.get("").toAbsolutePath()}.")
    }
}
