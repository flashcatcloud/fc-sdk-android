/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */
@file:Suppress("StringLiteralDuplication")

import cloud.flashcat.gradle.config.androidLibraryConfig
import cloud.flashcat.gradle.config.dependencyUpdateConfig
import cloud.flashcat.gradle.config.detektCustomConfig
import cloud.flashcat.gradle.config.javadocConfig
import cloud.flashcat.gradle.config.junitConfig
import cloud.flashcat.gradle.config.kotlinConfig
import cloud.flashcat.gradle.config.publishingConfig
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // Build
    id("com.android.library")
    kotlin("android")
    id("com.google.devtools.ksp")

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
    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }

    namespace = "cloud.flashcat.android.sessionreplay"
}

dependencies {
    api(project(":dd-sdk-android-core"))
    implementation(project(":dd-sdk-android-internal"))
    implementation(libs.okHttp)
    implementation(libs.kotlin)
    implementation(libs.gson)
    implementation(libs.androidXAppCompat)

    ksp(project(":tools:noopfactory"))

    testImplementation(project(":tools:unit")) {
        attributes {
            attribute(
                com.android.build.api.attributes.ProductFlavorAttr.of("platform"),
                objects.named("jvm")
            )
        }
    }
    testImplementation(testFixtures(project(":dd-sdk-android-core")))
    testImplementation(libs.okHttp)
    testImplementation(libs.bundles.jUnit5)
    testImplementation(libs.bundles.testTools)
    unmock(libs.robolectric)
}

unMock {
    keep("android.widget.ImageView\$ScaleType")
    keep("android.graphics.Rect")
    keep("android.graphics.drawable.GradientDrawable")
}

apply(from = "clone_session_replay_schema.gradle.kts")
apply(from = "generate_session_replay_models.gradle.kts")

kotlinConfig(jvmBytecodeTarget = JvmTarget.JVM_11)
androidLibraryConfig()
junitConfig()
javadocConfig()
dependencyUpdateConfig()
publishingConfig(
    "The Session Replay feature to use with the FlashCat monitoring " +
        "library for Android applications.",
    customArtifactId = "fc-sdk-android-session-replay"
)
detektCustomConfig()
