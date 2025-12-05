/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */
@file:Suppress("StringLiteralDuplication")

import com.android.build.gradle.LibraryExtension
import cloud.flashcat.gradle.config.AndroidConfig
import cloud.flashcat.gradle.config.registerSubModuleAggregationTask
import org.gradle.api.internal.file.UnionFileTree
import org.gradle.api.internal.tasks.DefaultTaskDependencyFactory
import java.util.Properties

plugins {
    `maven-publish`
    signing
}

version = AndroidConfig.VERSION.name

buildscript {
    repositories {
        google()
        mavenCentral()
        maven { setUrl(cloud.flashcat.gradle.Dependencies.Repositories.Gradle) }
    }

    dependencies {
        classpath(libs.androidToolsGradlePlugin)
        classpath(libs.kotlinGradlePlugin)
        classpath(libs.kotlinSPGradlePlugin)
        classpath(libs.dokkaGradlePlugin)
        classpath(libs.unmockGradlePlugin)
        classpath(libs.sqlDelightGradlePlugin)
        classpath(libs.binaryCompatibilityGradlePlugin)
        classpath(libs.kotlinxSerializationPlugin)
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven { setUrl(cloud.flashcat.gradle.Dependencies.Repositories.Jitpack) }
    }
}

// Maven Central Portal (2024+) configuration for SNAPSHOT publishing
// Using Gradle's maven-publish plugin with snapshot repository
//
// Credentials must be set via environment variables:
// - MAVEN_CENTRAL_USERNAME: Your Maven Central Portal username (from generated token)
// - MAVEN_CENTRAL_PASSWORD: Your Maven Central Portal password (from generated token)
//
// Publishing workflow:
// 1. For SNAPSHOT: ./gradlew publishAllPublicationsToSnapshotRepository
// 2. For RELEASE: Upload to Maven Central Portal UI manually

// Load credentials from environment variables only
val mavenCentralUsername = System.getenv("MAVEN_CENTRAL_USERNAME")
val mavenCentralPassword = System.getenv("MAVEN_CENTRAL_PASSWORD")

val signingKeyEnv = System.getenv("GPG_PRIVATE_KEY")
val signingPassword = System.getenv("GPG_PASSWORD")

// Configure snapshot repository for all subprojects
subprojects {
    plugins.withId("maven-publish") {
        configure<PublishingExtension> {
            repositories {
                maven {
                    name = "Snapshot"
                    url = uri("https://central.sonatype.com/repository/maven-snapshots/")
                    credentials {
                        username = mavenCentralUsername
                        password = mavenCentralPassword
                    }
                }
            }
        }

        if (!signingKeyEnv.isNullOrEmpty()) {
            apply(plugin = "signing")
            configure<SigningExtension> {
                try {
                    val decodedKey = String(java.util.Base64.getDecoder().decode(signingKeyEnv))
                    useInMemoryPgpKeys(decodedKey, signingPassword)
                } catch (e: Exception) {
                    // 如果不是 Base64，尝试直接使用（防止你存的是纯文本）
                    useInMemoryPgpKeys(signingKeyEnv, signingPassword)
                }

                sign(extensions.getByType<PublishingExtension>().publications)
            }
        }
    }
}

// Convenience task to publish all snapshot versions
tasks.register("publishAllSnapshots") {
    description = "Publish all SNAPSHOT versions to Maven Central Portal"
    group = "publishing"
    
    dependsOn(subprojects.mapNotNull {
        it.tasks.findByName("publishAllPublicationsToSnapshotRepository")
    })
}

tasks.named<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

tasks.register("checkAll") {
    dependsOn(
        "lintCheckAll",
        "unitTestAll",
        "instrumentTestAll"
    )
}

registerSubModuleAggregationTask("assembleLibraries", "assemble")
registerSubModuleAggregationTask("assembleLibrariesDebug", "assembleDebug")
registerSubModuleAggregationTask("assembleLibrariesRelease", "assembleRelease")

registerSubModuleAggregationTask("unitTestRelease", "testReleaseUnitTest")
registerSubModuleAggregationTask(
    "unitTestReleaseFeatures",
    "testReleaseUnitTest",
    ":features:"
)
registerSubModuleAggregationTask("unitTestReleaseIntegrations", "testReleaseUnitTest", ":integrations:")

registerSubModuleAggregationTask("unitTestDebug", "testDebugUnitTest")
registerSubModuleAggregationTask(
    "unitTestDebugFeatures",
    "testDebugUnitTest",
    ":features:"
)
registerSubModuleAggregationTask("unitTestDebugIntegrations", "testDebugUnitTest", ":integrations:")
tasks.register("unitTestDebugSamples") {
    dependsOn(
        ":sample:benchmark:testDebugUnitTest"
    )
}

tasks.register("assembleSampleRelease") {
    dependsOn(
        ":sample:kotlin:assembleUs1Release",
        ":sample:wear:assembleUs1Release",
        ":sample:vendor-lib:assembleRelease",
        ":sample:automotive:assembleRelease",
        ":sample:tv:assembleRelease"
    )
}

tasks.register("unitTestTools") {
    dependsOn(
        ":tools:unit:testJvmReleaseUnitTest",
        ":tools:detekt:test",
        ":tools:lint:test",
        ":tools:noopfactory:test",
        ":tools:benchmark:test"
    )
}

tasks.register("unitTestAll") {
    dependsOn(
        ":unitTestDebug",
        ":unitTestRelease",
        ":unitTestTools"
    )
}

registerSubModuleAggregationTask("lintCheckAll", "lintRelease") {
    dependsOn(":tools:lint:lint")
}
registerSubModuleAggregationTask("checkDependencyLicencesAll", "checkDependencyLicenses")

registerSubModuleAggregationTask("checkApiSurfaceChangesAll", "checkApiSurfaceChanges")

registerSubModuleAggregationTask("checkTransitiveDependenciesListAll", "checkTransitiveDependenciesList")

/**
 * Task necessary to be compliant with the shared Android static analysis pipeline
 */
tasks.register("checkGeneratedFiles") {
    dependsOn("checkDependencyLicencesAll")
    dependsOn("checkApiSurfaceChangesAll")
    dependsOn("checkTransitiveDependenciesListAll")
}

registerSubModuleAggregationTask("koverReportAll", "koverXmlReportRelease")
registerSubModuleAggregationTask("koverReportFeatures", "koverXmlReportRelease", ":features:")
registerSubModuleAggregationTask("koverReportIntegrations", "koverXmlReportRelease", ":integrations:")

registerSubModuleAggregationTask("printDetektClasspathAll", "printDetektClasspath")
registerSubModuleAggregationTask("printDetektClasspathFeatures", "printDetektClasspath", ":features:")
registerSubModuleAggregationTask("printDetektClasspathIntegrations", "printDetektClasspath", ":integrations:")

tasks.register("instrumentTestAll") {
    dependsOn(":instrumented:integration:connectedCheck")
}

tasks.register("buildIntegrationTestsArtifacts") {
    dependsOn(":instrumented:integration:assembleDebugAndroidTest")
    dependsOn(":instrumented:integration:assembleDebug")
}

tasks.register("buildNdkIntegrationTestsArtifacts") {
    dependsOn(":features:dd-sdk-android-ndk:assembleDebugAndroidTest")
    // we need this artifact to trick Bitrise
    dependsOn(":instrumented:integration:assembleDebug")
}

tasks.register("printSdkDebugRuntimeClasspath") {
    val fileTreeClassPathCollector = UnionFileTree(
        DefaultTaskDependencyFactory.withNoAssociatedProject()
    )
    val nonFileTreeClassPathCollector = mutableListOf<FileCollection>()

    allprojects.minus(project).forEach { subproject ->
        val childTask = subproject.tasks.register("printDebugRuntimeClasspath") {
            doLast {
                val ext =
                    subproject.extensions.findByType(LibraryExtension::class.java) ?: return@doLast
                val classpath = ext.libraryVariants
                    .filter { it.name == "jvmDebug" || it.name == "debug" }
                    .map { libVariant ->
                        // returns also test part of classpath for now, no idea how to filter it out
                        libVariant.getCompileClasspath(null).filter { it.exists() }
                    }
                    .first()
                if (classpath is FileTree) {
                    fileTreeClassPathCollector.addToUnion(classpath)
                } else {
                    nonFileTreeClassPathCollector += classpath
                }
            }
        }
        this@register.dependsOn(childTask)
    }
    doLast {
        val fileCollections = mutableListOf<FileCollection>()
        fileCollections.addAll(nonFileTreeClassPathCollector)
        if (!fileTreeClassPathCollector.isEmpty) {
            fileCollections.add(fileTreeClassPathCollector)
        }
        val result = fileCollections.flatMap {
            it.files
        }.toMutableSet()

        val localPropertiesFile = File(project.rootDir, "local.properties")
        if (localPropertiesFile.exists()) {
            val localProperties = Properties().apply {
                localPropertiesFile.inputStream().use { load(it) }
            }
            val sdkDirPath = localProperties["sdk.dir"]
            val androidJarFilePath = listOf(
                sdkDirPath,
                "platforms",
                "android-${AndroidConfig.TARGET_SDK}",
                "android.jar"
            )
            result += File(androidJarFilePath.joinToString(File.separator))
        }

        val envSdkHome = System.getenv("ANDROID_SDK_ROOT")
        if (!envSdkHome.isNullOrBlank()) {
            val androidJarFilePath = listOf(
                envSdkHome,
                "platforms",
                "android-${AndroidConfig.TARGET_SDK}",
                "android.jar"
            )
            result += File(androidJarFilePath.joinToString(File.separator))
        }

        File("sdk_classpath").writeText(result.joinToString(File.pathSeparator) { it.absolutePath })
    }
}

tasks.register("listAllPublishedArtifactIds") {
    doLast {
        val artifactIds = rootProject.subprojects.flatMap { subproject ->
            val publishing = subproject.extensions.findByType<PublishingExtension>()
            publishing?.publications?.mapNotNull { publication ->
                if (publication is MavenPublication) {
                    publication.artifactId
                } else {
                    null
                }
            }.orEmpty()
        }
        artifactIds.forEach {
            println(it)
        }
    }
}


