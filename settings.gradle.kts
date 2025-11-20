/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

// CORE LIBRARY
include(":fc-sdk-android-core")
include(":fc-sdk-android-internal")

// MAIN FEATURE LIBRARIES
include(":features:fc-sdk-android-trace-api")
include(":features:fc-sdk-android-trace-internal")
include(":features:fc-sdk-android-rum")
include(":features:fc-sdk-android-logs")
include(":features:fc-sdk-android-ndk")
include(":features:fc-sdk-android-trace")
include(":features:fc-sdk-android-webview")
include(":features:fc-sdk-android-session-replay")
include(":features:fc-sdk-android-session-replay-compose")
include(":features:fc-sdk-android-session-replay-material")
include(":features:fc-sdk-android-trace-otel")
include(":features:fc-sdk-android-flags")

// INTEGRATION LIBRARIES
include(":integrations:fc-sdk-android-apollo")
include(":integrations:fc-sdk-android-coil")
include(":integrations:fc-sdk-android-compose")
include(":integrations:fc-sdk-android-fresco")
include(":integrations:fc-sdk-android-glide")
include(":integrations:fc-sdk-android-rx")
include(":integrations:fc-sdk-android-sqldelight")
include(":integrations:fc-sdk-android-timber")
include(":integrations:fc-sdk-android-tv")
include(":integrations:fc-sdk-android-okhttp")
include(":integrations:fc-sdk-android-okhttp-otel")
include(":integrations:fc-sdk-android-rum-coroutines")
include(":integrations:fc-sdk-android-trace-coroutines")

// TESTING UTILS
include(":reliability:stub-core")
include(":reliability:stub-feature")

// SINGLE FEATURE INTEGRATION TESTS
include(":reliability:single-fit:logs")
include(":reliability:single-fit:rum")
include(":reliability:single-fit:trace")
include(":reliability:single-fit:okhttp")

// CORE INTEGRATION TESTS
include(":reliability:core-it")

// LEGACY TESTS
include(":instrumented:integration")

// SAMPLE PROJECTS
include(":sample:kotlin")
include(":sample:tv")
include(":sample:wear")
include(":sample:vendor-lib")
include(":sample:benchmark")
include(":sample:automotive")

// TOOLCHAIN
include(":tools:detekt")
include(":tools:unit")
include(":tools:noopfactory")
include(":tools:javabackport")
include(":tools:lint")
include(":tools:benchmark")
