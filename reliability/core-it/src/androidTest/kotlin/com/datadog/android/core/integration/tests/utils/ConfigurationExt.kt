/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.core.integration.tests.utils

import com.flashcat.rum.FlashcatSite
import com.flashcat.rum.core.configuration.Configuration
import com.flashcat.rum.trace.TracingHeaderType
import com.flashcat.tools.unit.getFieldValue

fun Configuration.site(): FlashcatSite {
    return this.getFieldValue<Any, Configuration>("coreConfig").getFieldValue("site")
}

fun Configuration.clientToken(): String {
    return this.getFieldValue<String, Configuration>("clientToken")
}

fun Configuration.env(): String {
    return this.getFieldValue<String, Configuration>("env")
}

fun Configuration.variant(): String {
    return this.getFieldValue<String, Configuration>("variant")
}

fun Configuration.service(): String? {
    return this.getFieldValue<String?, Configuration>("service")
}

fun Configuration.isCrashReportsEnabled(): Boolean {
    return this.getFieldValue<Boolean, Configuration>("crashReportsEnabled")
}

fun Configuration.getFirstPartyHostsWithHeaderTypes(): Map<String, Set<TracingHeaderType>> {
    return this.getFieldValue<Any, Configuration>("coreConfig")
        .getFieldValue("firstPartyHostsWithHeaderTypes")
}

fun Configuration.isDeveloperModeEnabled(): Boolean {
    return this.getFieldValue<Any, Configuration>("coreConfig")
        .getFieldValue("enableDeveloperModeWhenDebuggable")
}

fun Configuration.additionalConfig(): Map<String, Any> {
    return this.getFieldValue<Map<String, Any>, Configuration>("additionalConfig")
}
