/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.benchmark

/**
 * Enumeration of FlashCat metrics, traces endpoints.
 */
enum class EndPoint(
    private val metrics: String,
    private val traces: String
) {

    CN(
        metrics = "https://browser.flashcat.cloud/",
        traces = "https://browser.flashcat.cloud/"
    ),
    STAGING(
        metrics = "https://jira.flashcat.cloud/",
        traces = "https://jira.flashcat.cloud/"
    );

    /**
     * Gets the url for submitting metrics.
     */
    fun metricUrl(): String = metrics + "api/v2/series"

    /**
     * Gets the url for submitting traces.
     */
    fun tracesUrl(): String = traces + "api/v2/spans"
}
