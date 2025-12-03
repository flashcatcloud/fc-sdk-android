import cloud.flashcat.gradle.config.androidLibraryConfig
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
    id("org.jetbrains.dokka")

    // Analysis tools
    id("com.github.ben-manes.versions")

    // Tests
    id("org.jetbrains.kotlinx.kover")

    // Internal Generation
    id("com.datadoghq.dependency-license")
    id("apiSurface")
    id("transitiveDependencies")
    id("verificationXml")
}

android {
    namespace = "cloud.flashcat.android.apollo"
}

dependencies {
    implementation(project(":dd-sdk-android-internal"))
    implementation(libs.apolloRuntime)
    implementation(libs.kotlin)
    implementation(libs.okHttp)

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
    testImplementation(libs.okHttpMock)
}

kotlinConfig(jvmBytecodeTarget = JvmTarget.JVM_11)
junitConfig()
androidLibraryConfig()
publishingConfig(
    projectDescription = "An Apollo interceptor for handling GraphQL requests to use with the " +
        "FlashCat monitoring library for Android applications.",
    customArtifactId = "fc-sdk-android-apollo"
)
