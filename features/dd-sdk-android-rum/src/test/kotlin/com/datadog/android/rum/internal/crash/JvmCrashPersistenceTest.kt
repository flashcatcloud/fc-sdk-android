/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.crash

import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.rum.model.ErrorEvent
import com.google.gson.JsonParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

internal class JvmCrashPersistenceTest {
    @TempDir lateinit var directory: File
    private val id = "12345678-1234-1234-1234-123456789abc"
    private val bytes get() = """
        {"date":1234,"type":"error",
        "error":{"id":"$id","is_crash":true,"stack":"original stack"}}
    """.trimIndent().toByteArray()

    @Test
    fun `M retain the complete report W process dies before beacon outcome`() {
        val store = JvmCrashPersistence { directory }
        val pending = store.begin(id, 1000)
        store.onWritten(RawBatchEvent(bytes))
        assertThat(pending.awaitReport()?.getAsJsonObject("error")?.get("stack")?.asString)
            .isEqualTo("original stack")
        // A new process has no pending wait and no result file. Original report is still uploaded.
        assertThat(JvmCrashPersistence { directory }.enrich(bytes)).isEqualTo(bytes)
    }

    @Test
    fun `M enrich after restart W outcome persisted without rewriting full report`() {
        val store = JvmCrashPersistence { directory }
        val pending = store.begin(id, 1000)
        store.onWritten(RawBatchEvent(bytes))
        store.finish(pending, ErrorEvent.Beacon("sent", 42))
        val enriched = JsonParser.parseString(String(JvmCrashPersistence { directory }.enrich(bytes))).asJsonObject
        assertThat(enriched.get("date").asLong).isEqualTo(1234)
        assertThat(enriched.getAsJsonObject("error").get("stack").asString).isEqualTo("original stack")
        assertThat(enriched.getAsJsonObject("error").getAsJsonObject("beacon").get("status").asString)
            .isEqualTo("sent")
        assertThat(File(directory, id).readText()).doesNotContain("original stack")
    }

    @Test
    fun `M skip network preparation W unrelated report or write timed out`() {
        val store = JvmCrashPersistence { directory }
        val pending = store.begin(id, 0)
        store.onWritten(RawBatchEvent(String(bytes).replace(id, "another-id").toByteArray()))
        assertThat(pending.awaitReport()).isNull()
        store.finish(pending, null)
        assertThat(directory.listFiles()).isEmpty()
    }

    @Test
    fun `M preserve report W sidecar corrupt missing or date mismatched`() {
        val store = JvmCrashPersistence { directory }
        for (content in listOf("broken", "{}", """{"id":"$id","date":999,"beacon":{"status":"sent"}}""")) {
            File(directory, id).writeText(content)
            assertThat(store.enrich(bytes)).isEqualTo(bytes)
        }
        for (content in listOf("broken", "{}", "{\"error\":5}")) {
            assertThat(store.enrich(content.toByteArray())).isEqualTo(content.toByteArray())
        }
    }

    @Test
    fun `M retain report W result persistence fails`() {
        val blocker = File(directory, "not-a-directory").apply { writeText("x") }
        val store = JvmCrashPersistence { blocker }
        val pending = store.begin(id, 1000)
        store.onWritten(RawBatchEvent(bytes))
        store.finish(pending, ErrorEvent.Beacon("timeout", 900))
        assertThat(pending.finished.count).isZero()
        assertThat(store.enrich(bytes)).isEqualTo(bytes)
    }
}
