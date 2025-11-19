import com.flashcat.gradle.config.androidLibraryConfig
import com.flashcat.gradle.config.dependencyUpdateConfig
import com.flashcat.gradle.config.detektCustomConfig
import com.flashcat.gradle.config.java11
import com.flashcat.gradle.config.javadocConfig
import com.flashcat.gradle.config.junitConfig
import com.flashcat.gradle.config.kotlinConfig
import com.flashcat.gradle.config.publishingConfig
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
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
}

android {
    namespace = "com.flashcat.rum.internal"
    compileOptions {
        java11()
    }

    testFixtures {
        enable = true
    }
}

dependencies {
    implementation(libs.kotlin)

    // Generate NoOp implementations
    ksp(project(":tools:noopfactory"))
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
    testFixturesImplementation(libs.kotlin)
    testFixturesImplementation(libs.bundles.jUnit5)
    testFixturesImplementation(libs.bundles.testTools)
    testFixturesImplementation(project(":tools:unit")) {
        attributes {
            attribute(
                com.android.build.api.attributes.ProductFlavorAttr.of("platform"),
                objects.named("jvm")
            )
        }
    }
    unmock(libs.robolectric)
}

kotlinConfig(jvmBytecodeTarget = JvmTarget.JVM_11)
androidLibraryConfig()
junitConfig()
javadocConfig()
dependencyUpdateConfig()
publishingConfig(
    "Internal library to be used by the Datadog SDK modules."
)
detektCustomConfig()

unMock {
    keep("android.os.BaseBundle")
    keep("android.os.Bundle")
    keep("android.os.Parcel")
    keepStartingWith("com.android.internal.util.")
    keepStartingWith("android.util.")
    keep("android.content.ComponentName")
    keep("android.content.ContentProvider")
    keep("android.content.IContentProvider")
    keep("android.content.ContentProviderNative")
    keep("android.net.Uri")
    keep("android.os.Handler")
    keep("android.os.IMessenger")
    keep("android.os.Looper")
    keep("android.os.Message")
    keep("android.os.MessageQueue")
    keep("android.os.SystemProperties")
    keep("android.view.DisplayEventReceiver")
    keepStartingWith("org.json")
}
