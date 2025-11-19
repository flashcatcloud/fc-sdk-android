/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.flashcat.gradle.config.androidLibraryConfig
import com.flashcat.gradle.config.dependencyUpdateConfig
import com.flashcat.gradle.config.detektCustomConfig
import com.flashcat.gradle.config.javadocConfig
import com.flashcat.gradle.config.junitConfig
import com.flashcat.gradle.config.kotlinConfig
import com.flashcat.gradle.config.publishingConfig
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // Build
    id("com.android.library")
    kotlin("android")

    // Publish
    `maven-publish`
    signing
    id("org.jetbrains.dokka-javadoc")

    // Analysis tools
    id("com.github.ben-manes.versions")

    // Tests
    id("org.jetbrains.kotlinx.kover")

    // Internal Generation
    id("com.datadoghq.dependency-license")
    id("apiSurface")
    id("transitiveDependencies")
    id("verificationXml")
    id("binary-compatibility-validator")
}

android {
    namespace = "com.flashcat.rum.sqldelight"
}

dependencies {
    implementation(project(":features:fc-sdk-android-trace"))
    implementation(project(":features:fc-sdk-android-rum"))
    implementation(libs.kotlin)
    implementation(libs.okHttp)
    implementation(libs.sqlDelight)

    testImplementation(project(":tools:unit")) {
        attributes {
            attribute(
                com.android.build.api.attributes.ProductFlavorAttr.of("platform"),
                objects.named("jvm")
            )
        }
    }
    testImplementation(libs.bundles.jUnit5)
    testImplementation(libs.bundles.testTools)
    testImplementation(libs.okHttpMock)
    testImplementation(testFixtures(project(":features:fc-sdk-android-trace")))
}

kotlinConfig(jvmBytecodeTarget = JvmTarget.JVM_11)
androidLibraryConfig()
junitConfig()
javadocConfig()
dependencyUpdateConfig()
publishingConfig(
    "A SQLDelight integration to use with the Datadog monitoring library for Android applications."
)
detektCustomConfig()
