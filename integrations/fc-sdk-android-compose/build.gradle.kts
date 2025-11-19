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
import com.flashcat.gradle.config.taskConfig
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
    namespace = "com.flashcat.rum.compose"
    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":fc-sdk-android-internal"))
    implementation(project(":features:fc-sdk-android-rum"))
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
    "A Jetpack Compose integration to use with the Datadog monitoring library" +
        " for Android applications."
)
detektCustomConfig()
