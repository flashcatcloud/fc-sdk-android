/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.config

import com.android.build.api.dsl.CompileOptions
import com.android.build.gradle.LibraryExtension
import com.datadog.gradle.plugin.licenses.DependencyLicensesExtension
import com.datadog.gradle.utils.Version
import org.gradle.api.JavaVersion
import org.gradle.api.Project

object AndroidConfig {

    const val TARGET_SDK = 36
    const val MIN_SDK = 23
    const val MIN_SDK_FOR_AUTO = 29
    const val BUILD_TOOLS_VERSION = "36.0.0"

    /**
     * Determine version based on CI environment variables (GitHub Actions)
     * - Tag (GITHUB_REF_TYPE == "tag") → Release version parsed from tag (e.g., v0.3.0 -> 0.3.0)
     * - publish-new branch → Snapshot version (e.g., 0.3.0-SNAPSHOT)
     * - Other → Default snapshot version for local development
     */
    val VERSION = determineVersion()

    private fun determineVersion(): Version {
        // Check GitHub Actions variables
        val githubRefType = System.getenv("GITHUB_REF_TYPE")
        val githubRefName = System.getenv("GITHUB_REF_NAME")

        return when {
            // GitHub Actions: ref type is tag and ref name starts with 'v'
            githubRefType == "tag" && githubRefName?.startsWith("v") == true -> {
                parseVersionFromTag(githubRefName)
            }
            // Local development or other branches → Snapshot
            else -> {
                Version(0, 4, 0, Version.Type.Snapshot)
            }
        }
    }

    private fun parseVersionFromTag(tag: String): Version {
        // Remove 'v' prefix if present (e.g., v0.3.0 -> 0.3.0)
        val versionString = tag.removePrefix("v").trim()
        val parts = versionString.split(".")

        return Version(
            major = parts.getOrNull(0)?.toIntOrNull() ?: 0,
            minor = parts.getOrNull(1)?.toIntOrNull() ?: 0,
            hotfix = parts.getOrNull(2)?.toIntOrNull() ?: 0,
            type = Version.Type.Release
        )
    }
}

// TODO RUM-628 Switch to Java 17 bytecode
fun CompileOptions.java11() {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

fun CompileOptions.java17() {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

fun Project.androidLibraryConfig() {
    extensionConfig<LibraryExtension> {
        compileSdk = AndroidConfig.TARGET_SDK
        buildToolsVersion = AndroidConfig.BUILD_TOOLS_VERSION

        defaultConfig {
            minSdk = AndroidConfig.MIN_SDK
            consumerProguardFiles("${rootDir.absolutePath}/consumer-rules.pro")
        }

        compileOptions {
            java11()
        }

        sourceSets.all {
            java.srcDir("src/$name/kotlin")
        }
        sourceSets.named("main") {
            java.srcDir("build/generated/json2kotlin/main/kotlin")
        }
        libraryVariants.configureEach {
            addJavaSourceFoldersToModel(
                layout.buildDirectory.dir("generated/ksp/$name/kotlin").get().asFile
            )
        }

        @Suppress("UnstableApiUsage")
        testOptions {
            unitTests.isReturnDefaultValues = true
        }

        lint {
            warningsAsErrors = true
            abortOnError = true
            checkReleaseBuilds = false
            checkGeneratedSources = true
            ignoreTestSources = true
            val disabledChecks = listOf(
                // https://googlesamples.github.io/android-custom-lint-rules/checks/AndroidGradlePluginVersion.md.html
                "AndroidGradlePluginVersion",
                // https://googlesamples.github.io/android-custom-lint-rules/checks/GradleDependency.md.html
                "GradleDependency",
                // https://googlesamples.github.io/android-custom-lint-rules/checks/Aligned16KB.md.html
                "Aligned16KB",
                "UseKtx" // https://googlesamples.github.io/android-custom-lint-rules/checks/UseKtx.md.html
            )
            disable.addAll(disabledChecks)
        }

        packaging {
            resources {
                excludes += listOf(
                    "META-INF/jvm.kotlin_module",
                    "META-INF/LICENSE.md",
                    "META-INF/LICENSE-notice.md"
                )
            }
        }
    }

    extensionConfig<DependencyLicensesExtension> {
        transitiveDependencies = true
    }
}
