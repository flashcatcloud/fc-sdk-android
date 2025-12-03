/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

import cloud.flashcat.gradle.Dependencies
import cloud.flashcat.gradle.config.AndroidConfig
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

    defaultConfig {
        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {
                version = Dependencies.Versions.CMake
            }
        }
    }

    testOptions {
        targetSdk = AndroidConfig.TARGET_SDK
    }

    namespace = "cloud.flashcat.android.ndk"

    externalNativeBuild {
        cmake {
            path = File("$projectDir/CMakeLists.txt")
            version = Dependencies.Versions.CMake
        }
    }

    ndkVersion = Dependencies.Versions.Ndk
}

dependencies {
    implementation(project(":dd-sdk-android-internal"))
    api(project(":dd-sdk-android-core"))
    implementation(libs.kotlin)
    implementation(libs.okHttp)
    implementation(libs.androidXMultidex)

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

    androidTestImplementation(project(":tools:unit")) {
        attributes {
            attribute(
                com.android.build.api.attributes.ProductFlavorAttr.of("platform"),
                objects.named("art")
            )
        }
    }
    androidTestImplementation(libs.bundles.integrationTests)
    androidTestImplementation(libs.gson)
    androidTestImplementation(libs.assertJ)

    if (project.hasProperty(cloud.flashcat.gradle.Properties.USE_API21_JAVA_BACKPORT)) {
        // this is needed to make AssertJ working on APIs <24
        androidTestImplementation(project(":tools:javabackport"))
    }
}

kotlinConfig(jvmBytecodeTarget = JvmTarget.JVM_11)
androidLibraryConfig()
junitConfig()
javadocConfig()
dependencyUpdateConfig()
publishingConfig(
    "An NDK integration to use with the FlashCat monitoring library for Android applications.",
    customArtifactId = "fc-sdk-android-ndk"
)
detektCustomConfig()
