/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.config.AndroidConfig
import com.datadog.gradle.config.MavenConfig
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import java.util.Base64

plugins {
    `java-library`
    id("com.gradleup.shadow")
    `maven-publish`
    signing
    id("com.vanniktech.maven.publish.base")
}

dependencies {
    implementation(libs.jctools)
    implementation(libs.re2j)
}

tasks.named<Jar>("jar") {
    // Move the default (empty) jar out of the way to avoid name collision
    archiveClassifier.set("raw")
}

tasks.shadowJar {
    // Make shadowJar the primary artifact by removing the 'all' classifier
    archiveClassifier.set("")
    
    relocate("org.jctools", "cloud.flashcat.shaded.jctools")
    relocate("com.google.re2j", "cloud.flashcat.shaded.re2j")
    
    // Use runtimeClasspath which is resolvable
    configurations = listOf(project.configurations.runtimeClasspath.get())
}

// Use Vanniktech plugin ONLY for Maven Central Portal repository setup (not for artifact configuration)
// This ensures the same publishToSonatype / Central Portal API is used as other modules
configure<MavenPublishBaseExtension> {
    publishToMavenCentral(automaticRelease = false)
}

// Manual publication configuration with shadow jar as the artifact
publishing {
    publications {
        register<MavenPublication>("maven") {
            groupId = MavenConfig.GROUP_ID
            artifactId = project.name
            version = AndroidConfig.VERSION.name

            artifact(tasks.shadowJar)

            pom {
                name.set(project.name)
                description.set("Shaded dependencies for FlashCat Android SDK")
                inceptionYear.set("2026")
                url.set("https://github.com/flashcatcloud/fc-sdk-android/")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }

                organization {
                    name.set("FlashCat")
                    url.set("https://flashcat.cloud/")
                }

                developers {
                    developer {
                        id.set("flashcat")
                        name.set("FlashCat")
                        email.set("support@flashcat.cloud")
                        organization.set("FlashCat")
                        organizationUrl.set("https://flashcat.cloud/")
                    }
                }

                scm {
                    url.set("https://github.com/flashcatcloud/fc-sdk-android/")
                    connection.set("scm:git:git@github.com:flashcatcloud/fc-sdk-android.git")
                    developerConnection.set("scm:git:git@github.com:flashcatcloud/fc-sdk-android.git")
                }
            }
        }
    }
}

// Signing configuration (consistent with MavenConfig.publishingConfig())
signing {
    val isLocalPublish = gradle.startParameter.taskNames.any {
        it.contains("publishToMavenLocal", ignoreCase = true)
    }
    isRequired = !hasProperty("dd-skip-signing") && !isLocalPublish

    val privateKey = System.getenv("GPG_PRIVATE_KEY")
    val password = System.getenv("GPG_PASSWORD")

    if (privateKey != null && password != null) {
        val decodedKey = try {
            String(Base64.getDecoder().decode(privateKey))
        } catch (e: Exception) {
            privateKey // Already decoded / plain text
        }
        useInMemoryPgpKeys(decodedKey, password)
    }

    sign(publishing.publications["maven"])
}

// Force the shadowJar to be the ONLY exported artifact for this module
// This prevents R8 duplicate class errors while ensuring the file exists for KSP
configurations.apiElements {
    outgoing.artifacts.clear()
    outgoing.artifact(tasks.shadowJar)
}
configurations.runtimeElements {
    outgoing.artifacts.clear()
    outgoing.artifact(tasks.shadowJar)
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

if (tasks.findByName("assembleDebug") == null) {
    tasks.register("assembleDebug") {
        dependsOn("assemble")
    }
}

if (tasks.findByName("assembleRelease") == null) {
    tasks.register("assembleRelease") {
        dependsOn("assemble")
    }
}

// Shadow tasks to satisfy Android aggregation tasks
if (tasks.findByName("testDebugUnitTest") == null) {
    tasks.register("testDebugUnitTest") {
        dependsOn("test")
    }
}

if (tasks.findByName("testReleaseUnitTest") == null) {
    tasks.register("testReleaseUnitTest") {
        dependsOn("test")
    }
}

if (tasks.findByName("lintRelease") == null) {
    tasks.register("lintRelease") {
        // No-op for this Java library
    }
}

if (tasks.findByName("checkDependencyLicenses") == null) {
    tasks.register("checkDependencyLicenses") {
        // No-op for this Java library
    }
}

if (tasks.findByName("checkApiSurfaceChanges") == null) {
    tasks.register("checkApiSurfaceChanges") {
        // No-op for this Java library
    }
}

if (tasks.findByName("checkCompilerMetadataChanges") == null) {
    tasks.register("checkCompilerMetadataChanges") {
        // No-op for this Java library
    }
}

if (tasks.findByName("checkTransitiveDependenciesList") == null) {
    tasks.register("checkTransitiveDependenciesList") {
        // No-op for this Java library
    }
}

if (tasks.findByName("koverXmlReportRelease") == null) {
    tasks.register("koverXmlReportRelease") {
        // No-op or depends on koverXmlReport if applied
    }
}

if (tasks.findByName("printDetektClasspath") == null) {
    tasks.register("printDetektClasspath") {
        // No-op
    }
}
