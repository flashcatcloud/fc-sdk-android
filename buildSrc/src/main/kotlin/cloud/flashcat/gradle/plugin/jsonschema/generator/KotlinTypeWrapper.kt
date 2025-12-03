/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.gradle.plugin.jsonschema.generator

import cloud.flashcat.gradle.plugin.jsonschema.TypeDefinition
import com.squareup.kotlinpoet.TypeName

data class KotlinTypeWrapper(
    val name: String,
    val typeName: TypeName,
    val type: TypeDefinition
) {
    var written: Boolean = false
}
