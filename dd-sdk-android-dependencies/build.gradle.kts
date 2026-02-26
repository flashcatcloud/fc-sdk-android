/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

plugins {
    `java-library`
    id("com.gradleup.shadow")
}

dependencies {
    implementation(libs.jctools)
    implementation(libs.re2j)
}

tasks.shadowJar {
    archiveClassifier.set("all")
    
    relocate("org.jctools", "cloud.flashcat.shaded.jctools")
    relocate("com.google.re2j", "cloud.flashcat.shaded.re2j")
}

tasks.named<Jar>("jar") {
    dependsOn(tasks.shadowJar)
    from(zipTree(tasks.shadowJar.flatMap { it.archiveFile })) {
        // Exclude manifest from shadowJar to avoid conflicts with main jar's manifest
        exclude("META-INF/MANIFEST.MF")
    }
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
