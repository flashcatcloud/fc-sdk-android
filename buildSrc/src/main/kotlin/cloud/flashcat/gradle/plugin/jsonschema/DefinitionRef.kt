/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.gradle.plugin.jsonschema

import com.squareup.kotlinpoet.TypeName

data class DefinitionRef(
    val definition: JsonDefinition,
    val id: String,
    val typeName: TypeName,
    val className: String
)
