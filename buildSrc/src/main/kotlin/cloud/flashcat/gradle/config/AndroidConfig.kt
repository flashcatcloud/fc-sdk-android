/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.gradle.config

import com.android.build.api.dsl.CompileOptions
import com.android.build.gradle.LibraryExtension
import cloud.flashcat.gradle.utils.Version
import org.gradle.api.JavaVersion
import org.gradle.api.Project

object AndroidConfig {

    const val TARGET_SDK = 36
    const val MIN_SDK = 23
    const val MIN_SDK_FOR_AUTO = 29
    const val BUILD_TOOLS_VERSION = "36.0.0"

    /**
     * Determine version based on GitLab CI environment variables
     * - Tag (CI_COMMIT_TAG exists) → Release version (e.g., 0.1.0)
     * - public branch (CI_COMMIT_REF_NAME == "public") → Snapshot version (e.g., 0.1.0-SNAPSHOT)
     * - Other → Default snapshot version for local development
     */
    val VERSION = determineVersion()

    private fun determineVersion(): Version {
        val commitTag = System.getenv("CI_COMMIT_TAG")
        val refName = System.getenv("CI_COMMIT_REF_NAME")
        
        return when {
            // Tag release: v0.1.0 or 0.1.0 → 0.1.0 (Release)
            commitTag != null && commitTag.isNotEmpty() -> {
                parseVersionFromTag(commitTag)
            }
            // public branch → Snapshot
            refName == "public" -> {
                Version(0, 1, 0, Version.Type.Snapshot)
            }
            // Local development or other branches → Snapshot
            else -> {
                Version(0, 1, 0, Version.Type.Snapshot)
            }
        }
    }

    private fun parseVersionFromTag(tag: String): Version {
        // Remove 'v' prefix if present (e.g., v0.1.0 -> 0.1.0)
        val versionString = tag.removePrefix("v")
        val parts = versionString.split(".")
        
        return Version(
            major = parts.getOrNull(0)?.toIntOrNull() ?: 0,
            minor = parts.getOrNull(1)?.toIntOrNull() ?: 1,
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
            disable.addAll(
                listOf(
                    "UseKtx" // https://googlesamples.github.io/android-custom-lint-rules/checks/UseKtx.md.html
                )
            )
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

    // DependencyLicensesExtension is provided by com.datadoghq.dependency-license plugin
    // Configuration is done via the plugin's extension, not here
}
