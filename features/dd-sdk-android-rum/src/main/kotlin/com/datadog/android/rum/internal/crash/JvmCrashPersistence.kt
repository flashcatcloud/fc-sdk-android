/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.crash

import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.scope.RumSessionScope
import com.datadog.android.rum.model.ErrorEvent
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Observes the real batch write, including a WITHHELD session's release. The full report stays in
 * normal consent/encryption-managed storage. Only its id, date and beacon outcome go in a sidecar.
 * A missing/corrupt sidecar never prevents uploading the original report.
 */
internal class JvmCrashPersistence(private val directory: () -> File?) {
    private val pending = AtomicReference<Pending?>(null)

    // Read once on the upload worker. With no previous results, default-off uploads do not parse
    // every event or perform per-event disk reads. finish() adds new results in this process.
    private val resultIds by lazy {
        ConcurrentHashMap.newKeySet<String>().apply {
            directory()?.listFiles()?.filter { UUID_PATTERN.matches(it.name) }?.forEach { add(it.name) }
        }
    }

    internal class Pending(val id: String, val deadlineNs: Long) {
        val written = CountDownLatch(1)
        val finished = CountDownLatch(1)

        @Volatile var report: JsonObject? = null

        fun remainingMs(): Long = TimeUnit.NANOSECONDS.toMillis(deadlineNs - System.nanoTime()).coerceAtLeast(0)

        fun awaitReport(): JsonObject? {
            written.await(remainingMs(), TimeUnit.MILLISECONDS)
            return report
        }
    }

    fun begin(id: String, timeoutMs: Long): Pending =
        Pending(id, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)).also { pending.set(it) }

    /** Called only after EventBatchWriter.write returned true, never for an in-memory held event. */
    fun onWritten(event: RawBatchEvent) {
        val current = pending.get() ?: return
        val report = parse(event.data) ?: return
        if (report.getAsJsonObject("error")?.get("id")?.asString != current.id) return
        current.report = report
        current.written.countDown()
    }

    @Suppress("TooGenericExceptionCaught")
    fun finish(current: Pending, result: ErrorEvent.Beacon?) {
        try {
            val report = current.report
            if (report != null && result != null) {
                val dir = directory() ?: return
                if (!dir.exists() && !dir.mkdirs()) return
                // Same retention as RUM batches. Never store stacks, messages or user attributes here.
                dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > TTL_MS }
                    ?.forEach { it.delete() }
                val record = JsonObject().apply {
                    addProperty("id", current.id)
                    add("date", report.get("date"))
                    add("beacon", result.toJson())
                }
                val target = File(dir, current.id)
                val temporary = File(dir, current.id + ".tmp")
                FileOutputStream(temporary).use {
                    it.write(record.toString().toByteArray(Charsets.UTF_8))
                    it.fd.sync()
                }
                if (temporary.renameTo(target)) resultIds.add(current.id) else temporary.delete()
            }
        } catch (_: Exception) {
            // The full report is already safe. A result-write failure only loses diagnostics.
        } finally {
            current.finished.countDown()
            pending.compareAndSet(current, null)
        }
    }

    /** Wait only for this report's bounded, in-flight result; never hold a storage lock here. */
    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    fun enrich(data: ByteArray): ByteArray {
        try {
            return enrichReport(data)
        } catch (_: Exception) {
            return data
        }
    }

    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    private fun enrichReport(data: ByteArray): ByteArray {
        if (pending.get() == null && resultIds.isEmpty()) return data
        val report = parse(data) ?: return data
        val error = report.getAsJsonObject("error") ?: return data
        if (error.get("is_crash")?.asBoolean != true || error.has("beacon")) return data
        val id = error.get("id")?.asString ?: return data
        if (!UUID_PATTERN.matches(id)) return data
        try {
            pending.get()?.takeIf { it.id == id }?.let {
                it.finished.await(it.remainingMs(), TimeUnit.MILLISECONDS)
            }
            val dir = directory() ?: return data
            val file = File(dir, id)
            if (!file.isFile || System.currentTimeMillis() - file.lastModified() > TTL_MS) return data
            val record = JsonParser.parseString(file.readText()).asJsonObject
            if (record.get("id")?.asString != id || record.get("date") != report.get("date")) return data
            val beacon = record.getAsJsonObject("beacon") ?: return data
            // Validate before attaching; a truncated or invalid result must not damage a report.
            ErrorEvent.Beacon.fromJson(beacon.toString())
            error.add("beacon", beacon)
            return report.toString().toByteArray(Charsets.UTF_8)
        } catch (_: Exception) {
            return data
        }
    }

    companion object {
        private val UUID_PATTERN = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        private val TTL_MS = TimeUnit.HOURS.toMillis(24)

        @Suppress("TooGenericExceptionCaught")
        private fun parse(data: ByteArray): JsonObject? = try {
            JsonParser.parseString(String(data, Charsets.UTF_8)).asJsonObject
        } catch (_: Exception) {
            null
        }

        fun rumContext(report: JsonObject): RumContext {
            val view = report.getAsJsonObject("view")
            return RumContext(
                applicationId = report.getAsJsonObject("application").get("id").asString,
                sessionId = report.getAsJsonObject("session").get("id").asString,
                isSessionActive = true,
                sessionState = RumSessionScope.State.TRACKED,
                viewId = view.get("id").asString,
                viewName = view.get("name")?.asString,
                viewUrl = view.get("url").asString
            )
        }
    }
}
