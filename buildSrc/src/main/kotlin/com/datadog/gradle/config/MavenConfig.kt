/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.config

import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.findByType
import org.gradle.plugins.signing.SigningExtension

object MavenConfig {
    const val GROUP_ID = "cloud.flashcat"
    const val PUBLICATION = "release"
}

fun Project.publishingConfig(
    projectDescription: String,
    customArtifactId: String = name
) {
    val projectName = name

    // Apply Vanniktech plugin
    pluginManager.apply("com.vanniktech.maven.publish.base")

    // Configure Android Library publishing (sources + javadoc)
    configure<MavenPublishBaseExtension> {
        configure(
            AndroidSingleVariantLibrary(
                variant = "release",
                sourcesJar = true,
                publishJavadocJar = true
            )
        )

        // Coordinates
        coordinates(
            groupId = MavenConfig.GROUP_ID,
            artifactId = customArtifactId,
            version = AndroidConfig.VERSION.name
        )

        // POM configuration
        pom {
            name.set(projectName)
            description.set(projectDescription)
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

        // Publish to Maven Central Portal (2024+)
        // automaticRelease = false means it will create a deployment that needs manual approval in Portal UI
        publishToMavenCentral(automaticRelease = false)
    }

    // Manual signing configuration
    val signingExtension = extensions.findByType(SigningExtension::class)
    if (signingExtension == null) {
        logger.error("Missing signing extension for $projectName")
        return
    }

    signingExtension.apply {
        // Signing is required unless explicitly skipped or publishing to maven local
        val isLocalPublish = gradle.startParameter.taskNames.any {
            it.contains("publishToMavenLocal", ignoreCase = true)
        }
        isRequired = !hasProperty("dd-skip-signing") && !isLocalPublish

        val privateKey = System.getenv("GPG_PRIVATE_KEY")
        val password = System.getenv("GPG_PASSWORD")

        if (privateKey != null && password != null) {
            // Decode base64 if needed
            val decodedKey = try {
                String(java.util.Base64.getDecoder().decode(privateKey))
            } catch (e: Exception) {
                privateKey // Already decoded / plain text
            }
            useInMemoryPgpKeys(decodedKey, password)
        }
    }

    afterEvaluate {
        val publishingExtension = extensions.findByType<PublishingExtension>()
        if (publishingExtension == null) {
            logger.error("Missing publishing extension for $projectName")
            return@afterEvaluate
        }

        // Sign all publications (required by Maven Central)
        publishingExtension.publications.forEach { publication ->
            signingExtension.sign(publication)
        }
    }
}
