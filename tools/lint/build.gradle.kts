/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

import cloud.flashcat.gradle.config.dependencyUpdateConfig
import cloud.flashcat.gradle.config.junitConfig
import cloud.flashcat.gradle.config.kotlinConfig
import cloud.flashcat.gradle.config.taskConfig

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("com.github.ben-manes.versions")
    id("com.android.lint")
}

dependencies {
    compileOnly(libs.kotlin)
    compileOnly(libs.androidLintApi)
    compileOnly(libs.androidLintChecks)

    testImplementation(libs.androidLintTests)
    testImplementation(libs.androidLintApi)
    testImplementation(libs.bundles.jUnit5)
    testImplementation(libs.bundles.testTools)
}

kotlinConfig()
junitConfig()
dependencyUpdateConfig()

taskConfig<Jar> {
    manifest {
        attributes("Lint-Registry-v2" to "cloud.flashcat.android.lint.DatadogIssueRegistry")
    }
}
