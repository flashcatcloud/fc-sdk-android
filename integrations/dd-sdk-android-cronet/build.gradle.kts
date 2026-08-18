/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.config.androidLibraryConfig
import com.datadog.gradle.config.dependencyUpdateConfig
import com.datadog.gradle.config.detektCustomConfig
import com.datadog.gradle.config.javadocConfig
import com.datadog.gradle.config.junitConfig
import com.datadog.gradle.config.kotlinConfig
import com.datadog.gradle.config.publishingConfig
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
    id("de.mobilej.unmock")
    id("org.jetbrains.kotlinx.kover")

    // Internal Generation
    id("com.datadoghq.dependency-license")
    id("apiSurface")
    id("transitiveDependencies")
    id("verificationXml")
    id("binary-compatibility-validator")
}

android {
    namespace = "com.datadog.android.cronet"
}

dependencies {
    api(libs.cronetApi)
    implementation(libs.kotlin)
    implementation(libs.androidXAnnotation)

    implementation(project(":dd-sdk-android-internal"))
    implementation(project(":features:dd-sdk-android-rum"))

    unmock(libs.robolectric)
    // The tests exercise the newer Proxy API absent from the compile-time cronet version.
    // Pinned, not "+": a floating version resolves to whatever Chromium ships next and
    // has already broken this module's compilation once.
    testImplementation("${libs.cronetApi.get().module}:143.7445.0")
    testImplementation(testFixtures(project(":dd-sdk-android-core")))
    testImplementation(testFixtures(project(":dd-sdk-android-internal")))
    testImplementation(testFixtures(project(":features:dd-sdk-android-rum")))
    testImplementation(libs.elmyrJUnit4)
    testImplementation(libs.bundles.jUnit5)
    testImplementation(libs.bundles.testTools)
    testImplementation(libs.okHttp)
    testImplementation(libs.gson)
    testImplementation(project(":tools:unit")) {
        attributes {
            attribute(
                com.android.build.api.attributes.ProductFlavorAttr.of("platform"),
                objects.named("jvm")
            )
        }
    }
}

unMock {
    keepStartingWith("org.json")
}

kotlinConfig(jvmBytecodeTarget = JvmTarget.JVM_11)
androidLibraryConfig()
junitConfig()
javadocConfig()
dependencyUpdateConfig()
publishingConfig(
    "A Cronet monitoring integration to use with the Datadog monitoring library for Android applications."
)
detektCustomConfig()
