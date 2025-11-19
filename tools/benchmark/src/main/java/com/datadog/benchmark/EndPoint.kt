/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark

/**
 * Enumeration of datadog metrics, traces endpoints.
 */
enum class EndPoint(
    private val metrics: String,
    private val traces: String
) {

    US1(
        metrics = "https://api.flashcat.cloud/",
        traces = "https://browser-intake-flashcat.cloud/"
    ),
    US3(
        metrics = "https://api.us3.flashcat.cloud/",
        traces = "https://browser-intake-us3-flashcat.cloud/"
    ),
    US5(
        metrics = "https://api.us5.flashcat.cloud/",
        traces = "https://browser-intake-us5-flashcat.cloud/"
    ),
    EU1(
        metrics = "https://api.datadoghq.eu/",
        traces = "https://public-trace-http-intake.logs.datadoghq.eu/"
    ),
    AP1(
        metrics = "https://api.ap1.flashcat.cloud/",
        traces = "https://browser-intake-ap1-flashcat.cloud/"
    ),
    AP2(
        metrics = "https://api.ap2.flashcat.cloud/",
        traces = "https://browser-intake-ap2-flashcat.cloud/"
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
