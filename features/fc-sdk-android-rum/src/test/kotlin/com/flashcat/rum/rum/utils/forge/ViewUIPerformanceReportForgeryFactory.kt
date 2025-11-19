/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.rum.utils.forge

import com.flashcat.rum.rum.internal.collections.toEvictingQueue
import com.flashcat.rum.rum.internal.domain.state.SlowFrameRecord
import com.flashcat.rum.rum.internal.domain.state.ViewUIPerformanceReport
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class ViewUIPerformanceReportForgeryFactory : ForgeryFactory<ViewUIPerformanceReport> {
    override fun getForgery(forge: Forge): ViewUIPerformanceReport {
        val viewStartedTimeStamp = forge.aLong(min = 0)
        return ViewUIPerformanceReport(
            viewStartedTimeStamp = viewStartedTimeStamp,
            slowFramesRecords = forge.aList {
                SlowFrameRecord(
                    startTimestampNs = aLong(min = viewStartedTimeStamp),
                    durationNs = aLong(min = 0)
                )
            }.toEvictingQueue(),
            totalFramesDurationNs = forge.aLong(min = 1),
            slowFramesDurationNs = forge.aLong(min = 0),
            freezeFramesDuration = forge.aLong(min = 0),
            minViewLifetimeThresholdNs = 0
        )
    }
}
