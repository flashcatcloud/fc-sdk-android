/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.sdk.integration.trace

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import cloud.flashcat.android.Flashcat
import cloud.flashcat.android.api.context.DatadogContext
import cloud.flashcat.android.core.InternalSdkCore
import cloud.flashcat.android.log.Logs
import cloud.flashcat.android.sdk.integration.R
import cloud.flashcat.android.sdk.integration.RuntimeConfig
import cloud.flashcat.android.sdk.utils.getForgeSeed
import cloud.flashcat.android.sdk.utils.getTrackingConsent
import cloud.flashcat.android.trace.Trace
import cloud.flashcat.android.trace.api.span.DatadogSpan
import cloud.flashcat.android.trace.api.tracer.DatadogTracer
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

        Flashcat.setVerbosity(Log.VERBOSE)
        val sdkCore = checkNotNull(
            Flashcat.initialize(this, config, trackingConsent)
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

    fun getDatadogContext(): DatadogContext? {
        return (Flashcat.getInstance() as InternalSdkCore).getDatadogContext()
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
