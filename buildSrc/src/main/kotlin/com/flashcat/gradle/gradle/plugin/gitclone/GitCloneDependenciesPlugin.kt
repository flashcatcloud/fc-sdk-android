/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.gradle.plugin.gitclone

import org.gradle.api.Plugin
import org.gradle.api.Project

class GitCloneDependenciesPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        val extension = target.extensions
            .create(EXT_NAME, GitCloneDependenciesExtension::class.java)

        target.tasks
            .register(TASK_NAME, GitCloneDependenciesTask::class.java) {
                this.extension = extension
            }
    }

    companion object {
        const val EXT_NAME = "cloneDependencies"

        const val TASK_NAME = "cloneDependencies"
    }
}
