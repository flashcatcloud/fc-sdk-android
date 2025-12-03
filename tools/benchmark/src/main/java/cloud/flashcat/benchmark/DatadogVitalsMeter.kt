/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.benchmark

import cloud.flashcat.android.internal.profiler.GlobalBenchmark
import cloud.flashcat.benchmark.exporter.DatadogMetricExporter
import cloud.flashcat.benchmark.exporter.DatadogSpanExporter
import cloud.flashcat.benchmark.internal.reader.CPUVitalReader
import cloud.flashcat.benchmark.internal.reader.FpsVitalReader
import cloud.flashcat.benchmark.internal.reader.MemoryVitalReader
import cloud.flashcat.benchmark.internal.reader.VitalReader
import cloud.flashcat.benchmark.noop.NoOpObservableDoubleGauge
import cloud.flashcat.benchmark.profiler.DDBenchmarkProfiler
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.metrics.ObservableDoubleGauge
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import java.util.concurrent.TimeUnit

/**
 * This class is responsible for managing the performance gauges related to CPU, FPS, and memory usage.
 * It provides functionalities to start and stop monitoring these metrics, which will be uploaded to
 * Datadog metric API.
 */
class DatadogVitalsMeter private constructor(private val meter: Meter) : DatadogBaseMeter {

    private val cpuVitalReader: CPUVitalReader = CPUVitalReader()
    private val memoryVitalReader: MemoryVitalReader = MemoryVitalReader()
    private val fpsVitalReader: FpsVitalReader = FpsVitalReader()

    private val gaugesByMetricName: MutableMap<String, ObservableDoubleGauge> = mutableMapOf()

    /**
     * Starts cpu, memory and fps gauges.
     */
    override fun startMeasuring() {
        startGauge(cpuVitalReader)
        startGauge(memoryVitalReader)
        startGauge(fpsVitalReader)
    }

    /**
     * Stops cpu, memory and fps gauges.
     */
    override fun stopMeasuring() {
        stopGauge(cpuVitalReader)
        stopGauge(memoryVitalReader)
        stopGauge(fpsVitalReader)
    }

    private fun startGauge(reader: VitalReader) {
        synchronized(reader) {
            // Close the gauge if it exists before.
            val metricName = reader.metricName()
            gaugesByMetricName[metricName]?.close()
            reader.start()
            meter.gaugeBuilder(metricName).apply {
                reader.unit()?.let { unit ->
                    setUnit(unit)
                }
            }.buildWithCallback { observableDoubleMeasurement ->
                reader.readVitalData()?.let { data ->
                    observableDoubleMeasurement.record(data)
                }
            }.also { observableDoubleGauge ->
                gaugesByMetricName[metricName] = observableDoubleGauge
            }
        }
    }

    private fun stopGauge(reader: VitalReader) {
        synchronized(reader) {
            reader.stop()
            gaugesByMetricName[reader.metricName()]?.close()
            gaugesByMetricName[reader.metricName()] = NoOpObservableDoubleGauge()
        }
    }

    companion object {

        /**
         * Creates an instance of [DatadogVitalsMeter] with the given configuration.
         */
        fun create(datadogExporterConfiguration: DatadogExporterConfiguration): DatadogVitalsMeter {
            val datadogExporter = DatadogMetricExporter(datadogExporterConfiguration)
            val sdkMeterProvider = SdkMeterProvider.builder()
                .registerMetricReader(
                    PeriodicMetricReader.builder(datadogExporter)
                        .setInterval(datadogExporterConfiguration.intervalInSeconds, TimeUnit.SECONDS)
                        .build()
                )
                .build()
            val traceProvider = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(DatadogSpanExporter(datadogExporterConfiguration)).build())
                .build()
            val openTelemetry: OpenTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(traceProvider)
                .setMeterProvider(sdkMeterProvider)
                .build()
            GlobalOpenTelemetry.set(openTelemetry)
            GlobalBenchmark.register(DDBenchmarkProfiler())
            val meter = openTelemetry.getMeter(METER_INSTRUMENTATION_SCOPE_NAME)
            return DatadogVitalsMeter(meter)
        }

        private const val METER_INSTRUMENTATION_SCOPE_NAME = "datadog.open-telemetry"
    }
}
