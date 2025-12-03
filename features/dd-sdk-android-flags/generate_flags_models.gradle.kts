/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

import cloud.flashcat.gradle.plugin.apisurface.ApiSurfacePlugin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val generateFlagsModelsTaskName = "generateFlagsModelsFromJson"

tasks.register(
    generateFlagsModelsTaskName,
    cloud.flashcat.gradle.plugin.jsonschema.GenerateJsonSchemaTask::class.java
) {
    inputDirPath = "src/main/json/flags"
    targetPackageName = "cloud.flashcat.android.flags.model"
}

afterEvaluate {
    tasks.findByName(ApiSurfacePlugin.TASK_GEN_KOTLIN_API_SURFACE)
        ?.dependsOn(generateFlagsModelsTaskName)
    tasks.withType(KotlinCompile::class.java).configureEach {
        dependsOn(generateFlagsModelsTaskName)
    }
}
