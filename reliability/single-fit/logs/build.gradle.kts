/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.flashcat.gradle.config.androidLibraryConfig
import com.flashcat.gradle.config.dependencyUpdateConfig
import com.flashcat.gradle.config.junitConfig
import com.flashcat.gradle.config.kotlinConfig
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // Build
    id("com.android.library")
    kotlin("android")
    id("com.google.devtools.ksp")

    // Analysis tools
    id("com.github.ben-manes.versions")

    // Tests
    id("de.mobilej.unmock")
}

android {
    namespace = "com.flashcat.rum.logs.integration"
}

dependencies {
    implementation(project(":fc-sdk-android-core"))
    implementation(project(":features:fc-sdk-android-logs"))
    implementation(libs.kotlin)

    // Testing
    testImplementation(project(":tools:unit")) {
        attributes {
            attribute(
                com.android.build.api.attributes.ProductFlavorAttr.of("platform"),
                objects.named("jvm")
            )
        }
    }
    testImplementation(testFixtures(project(":fc-sdk-android-core")))
    testImplementation(project(":reliability:stub-core"))
    testImplementation(libs.bundles.jUnit5)
    testImplementation(libs.bundles.testTools)
    testImplementation(libs.okHttp)
    testImplementation(libs.gson)
    unmock(libs.robolectric)
}

unMock {
    keepStartingWith("android.os")
    keepStartingWith("org.json")
}

androidLibraryConfig()
kotlinConfig(jvmBytecodeTarget = JvmTarget.JVM_11)
junitConfig()
dependencyUpdateConfig()
