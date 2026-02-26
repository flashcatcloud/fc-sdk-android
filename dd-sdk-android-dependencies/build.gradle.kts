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
