/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sdk.integration.trace

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.flashcat.rum.Flashcat
import com.flashcat.rum.api.context.FlashcatContext
import com.flashcat.rum.core.InternalSdkCore
import com.flashcat.rum.log.Logs
import com.flashcat.rum.sdk.integration.R
import com.flashcat.rum.sdk.integration.RuntimeConfig
import com.flashcat.rum.sdk.utils.getForgeSeed
import com.flashcat.rum.sdk.utils.getTrackingConsent
import com.flashcat.rum.trace.Trace
import com.flashcat.rum.trace.api.span.DatadogSpan
import com.flashcat.rum.trace.api.tracer.DatadogTracer
import fr.xgouchet.elmyr.Forge
import java.util.LinkedList
import java.util.Random

internal class ActivityLifecycleTrace : AppCompatActivity() {

    private val forge by lazy { Forge().apply { seed = intent.getForgeSeed() } }

    private lateinit var tracer: DatadogTracer
    private val sentSpans = LinkedList<DatadogSpan>()
    private val sentLogs = LinkedList<Pair<Int, String>>()
    private lateinit var activityStartSpan: DatadogSpan
    private lateinit var activityResumeSpan: DatadogSpan

    // region Activity

    @Suppress("CheckInternal")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = RuntimeConfig.configBuilder().build()
        val trackingConsent = intent.getTrackingConsent()

        Datadog.setVerbosity(Log.VERBOSE)
        val sdkCore = checkNotNull(
            Datadog.initialize(this, config, trackingConsent)
        )

        listOf(
            { Logs.enable(RuntimeConfig.logsConfigBuilder().build(), sdkCore) },
            { Trace.enable(RuntimeConfig.tracesConfigBuilder().build(), sdkCore) }
        )
            .shuffled(Random(intent.getForgeSeed()))
            .forEach { it() }

        tracer = RuntimeConfig.tracer(sdkCore)
        setContentView(R.layout.main_activity_layout)
    }

    override fun onStart() {
        super.onStart()
        activityStartSpan = buildSpan(forge.anAlphabeticalString())
    }

    override fun onResume() {
        super.onResume()
        activityResumeSpan = buildSpan(forge.anAlphabeticalString())
    }

    override fun onPause() {
        super.onPause()
        activityResumeSpan.finish()
    }

    override fun onStop() {
        super.onStop()
        activityStartSpan.finish()
    }

    // endregion

    // region Tests

    fun getSentSpans(): LinkedList<DatadogSpan> {
        return sentSpans
    }

    fun getSentLogs(): LinkedList<Pair<Int, String>> {
        return sentLogs
    }

    fun getFlashcatContext(): FlashcatContext? {
        return (Datadog.getInstance() as InternalSdkCore).getFlashcatContext()
    }

    // endregion

    // region Internal

    private fun buildSpan(title: String): DatadogSpan {
        val span = tracer.buildSpan(title).start()
        checkNotNull(tracer.activateSpan(span)) { "Span activation failed" }
        val ddSpan = tracer.activeSpan() as DatadogSpan
        ddSpan.logMessage(title)
        sentLogs.add(Log.VERBOSE to title)
        sentSpans.add(ddSpan)
        return ddSpan
    }

    // endregion
}
