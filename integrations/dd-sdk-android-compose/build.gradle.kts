/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

import cloud.flashcat.gradle.config.androidLibraryConfig
import cloud.flashcat.gradle.config.dependencyUpdateConfig
import cloud.flashcat.gradle.config.detektCustomConfig
import cloud.flashcat.gradle.config.javadocConfig
import cloud.flashcat.gradle.config.junitConfig
import cloud.flashcat.gradle.config.kotlinConfig
import cloud.flashcat.gradle.config.publishingConfig
import cloud.flashcat.gradle.config.taskConfig
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // Build
    id("com.android.library")
    kotlin("android")
    alias(libs.plugins.composeCompilerPlugin)

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
}

android {
    namespace = "cloud.flashcat.android.compose"
    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":dd-sdk-android-internal"))
    implementation(project(":features:dd-sdk-android-rum"))
    implementation(libs.kotlin)

    implementation(platform(libs.androidXComposeBom))
    implementation(libs.androidXComposeRuntime)
    implementation(libs.androidXComposeMaterial)
    implementation(libs.androidXComposeNavigation)

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
    unmock(libs.robolectric)
}

unMock {
    keep("android.os.BaseBundle")
    keep("android.os.Bundle")
    keepStartingWith("android.util")
    keepStartingWith("com.android.internal.util")
}

kotlinConfig(jvmBytecodeTarget = JvmTarget.JVM_11)
androidLibraryConfig()
taskConfig<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        optIn.add("kotlin.RequiresOptIn")
    }
}
junitConfig()
javadocConfig()
dependencyUpdateConfig()
publishingConfig(
    "A Jetpack Compose integration to use with the FlashCat monitoring library" +
        " for Android applications.",
    customArtifactId = "fc-sdk-android-compose"
)
detektCustomConfig()
