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
    archiveClassifier.set("") 
    
    relocate("org.jctools", "cloud.flashcat.shaded.jctools")
    relocate("com.google.re2j", "cloud.flashcat.shaded.re2j")
}

tasks.named<Jar>("jar") {
    enabled = false
}

val shadowJar = tasks.shadowJar
artifacts {
    add("apiElements", shadowJar)
    add("runtimeElements", shadowJar)
}
