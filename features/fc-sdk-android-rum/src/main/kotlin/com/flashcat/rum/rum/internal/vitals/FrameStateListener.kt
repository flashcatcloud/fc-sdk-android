/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.flashcat.rum.rum.internal.vitals

import androidx.metrics.performance.JankStats

internal interface FrameStateListener : JankStats.OnFrameListener, FrameMetricsDataListener
