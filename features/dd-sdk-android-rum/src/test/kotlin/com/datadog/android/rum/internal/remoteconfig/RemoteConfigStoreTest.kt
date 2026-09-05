/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.remoteconfig

import android.content.Context
import android.content.SharedPreferences
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class RemoteConfigStoreTest {

    private lateinit var preferences: InMemorySharedPreferences
    private lateinit var appContext: Context

    @BeforeEach
    fun setUp() {
        preferences = InMemorySharedPreferences()
        appContext = mock()
        whenever(appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE))
            .thenReturn(preferences)
    }

    // region store key

    @Test
    fun `M cover everything that changes the answer W buildStoreKey()`() {
        val key = RemoteConfigStore.buildStoreKey(
            context = datadogContext(),
            intakeUrl = "https://rum.example.com/api/v2/rum",
            applicationId = "app-1"
        )

        assertThat(key).startsWith(RemoteConfigStore.STORE_KEY_PREFIX)
        assertThat(key).contains("rum.example.com")
        assertThat(key).contains("app-1")
        assertThat(key).contains(SERVICE)
        assertThat(key).contains(ENV)
        assertThat(key).contains(APP_VERSION)
    }

    @Test
    fun `M leave the sdk version out of the key W buildStoreKey()`() {
        // Including it would discard the stored values on every SDK upgrade and put the first
        // session after an upgrade back on the init values.
        val key = RemoteConfigStore.buildStoreKey(
            context = datadogContext(),
            intakeUrl = "https://rum.example.com/api/v2/rum",
            applicationId = "app-1"
        )

        assertThat(key).doesNotContain(SDK_VERSION)
    }

    @Test
    fun `M key by the endpoint host W buildStoreKey() { two intakes, two answers }`() {
        val context = datadogContext()

        val first = RemoteConfigStore.buildStoreKey(context, "https://rum-a.example.com/api/v2/rum", "app-1")
        val second = RemoteConfigStore.buildStoreKey(context, "https://rum-b.example.com/api/v2/rum", "app-1")

        assertThat(first).isNotEqualTo(second)
    }

    // endregion

    // region persistence

    @Test
    fun `M retain a snapshot after a later response is stored`() {
        val store = testedStore()
        val first = RemoteConfigValues(100f, 1, """{"cohort":"first"}""", "\"v1\"", 300L, true)
        val second = RemoteConfigValues(0f, 2, """{"cohort":"second"}""", "\"v2\"", 600L, false)
        store.store(first)
        val snapshot = testedStore().snapshot()

        store.store(second)

        assertThat(snapshot).isEqualTo(first)
        assertThat(store.snapshot()).isEqualTo(second)
    }

    @Test
    fun `M return absent values W snapshot before the first response`() {
        assertThat(testedStore().snapshot()).isEqualTo(RemoteConfigValues(null))
    }

    @Test
    fun `M read back on the next launch what a response stored W store()`() {
        testedStore().store(
            RemoteConfigValues(
                sessionSampleRate = 42f,
                version = 3,
                custom = """{"viplist":["u-1"]}""",
                etag = "\"v3\""
            )
        )

        // A fresh instance over the same preferences is what the next process start looks like.
        val nextLaunch = testedStore()
        assertThat(nextLaunch.sessionSampleRate()).isEqualTo(42f)
        assertThat(nextLaunch.appliedVersion()).isEqualTo(3)
        assertThat(nextLaunch.custom()).isEqualTo("""{"viplist":["u-1"]}""")
        assertThat(nextLaunch.etag()).isEqualTo("\"v3\"")
    }

    @Test
    fun `M answer absent before the first response W read`() {
        val store = testedStore()

        assertThat(store.sessionSampleRate()).isNull()
        assertThat(store.appliedVersion()).isNull()
        assertThat(store.custom()).isNull()
        assertThat(store.etag()).isNull()
    }

    @Test
    fun `M forget the knobs a response omitted W store()`() {
        // A knob nobody configured must go back to the init value, not linger at the last one.
        val store = testedStore()
        store.store(RemoteConfigValues(42f, 3, custom = """{"debug":true}""", etag = "\"v3\""))

        store.store(RemoteConfigValues(null, 4))

        assertThat(store.sessionSampleRate()).isNull()
        assertThat(store.custom()).isNull()
        assertThat(store.appliedVersion()).isEqualTo(4)
    }

    @Test
    fun `M keep the version W store() { remote configuration switched off }`() {
        val store = testedStore()
        store.store(RemoteConfigValues(42f, 3))

        store.store(RemoteConfigValues(null, 4))

        assertThat(store.appliedVersion()).isEqualTo(4)
    }

    @Test
    fun `M fall back to the init values W storage is unavailable`() {
        whenever(appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE))
            .thenThrow(SecurityException("no storage for you"))
        val store = RemoteConfigStore(appContext, "key", mock<InternalLogger>())

        store.store(RemoteConfigValues(42f, 3))

        assertThat(store.sessionSampleRate()).isNull()
        assertThat(store.appliedVersion()).isNull()
    }

    // endregion

    // region sweeping the entries of versions this device no longer runs

    @Test
    fun `M remove an entry nothing has refreshed for longer than the threshold W sweepAbandoned()`() {
        // The key covers the app version, so every release the device installs leaves one behind.
        storeUnder(OTHER_VERSION_KEY, atMs = NOW - THREE_DAYS_MS)
        val store = testedStore(nowMs = NOW)

        store.sweepAbandoned()

        assertThat(preferences.all.keys.filter { it.startsWith(OTHER_VERSION_KEY) }).isEmpty()
    }

    @Test
    fun `M keep an entry something refreshed recently W sweepAbandoned()`() {
        storeUnder(OTHER_VERSION_KEY, atMs = NOW - ONE_DAY_MS)
        val store = testedStore(nowMs = NOW)

        store.sweepAbandoned()

        assertThat(preferences.all.keys.filter { it.startsWith(OTHER_VERSION_KEY) }).isNotEmpty()
    }

    @Test
    fun `M remove every key of an abandoned entry W sweepAbandoned()`() {
        // An entry is spread over several keys, and leaving any of them behind leaks just as well.
        storeUnder(OTHER_VERSION_KEY, atMs = NOW - THREE_DAYS_MS)
        val before = preferences.all.keys.count { it.startsWith(OTHER_VERSION_KEY) }

        testedStore(nowMs = NOW).sweepAbandoned()

        assertThat(before).isGreaterThan(1)
        assertThat(preferences.all.keys.none { it.startsWith(OTHER_VERSION_KEY) }).isTrue()
    }

    @Test
    fun `M never remove its own entry W sweepAbandoned() { however old it looks }`() {
        // The one entry certainly in use: the caller is about to read it.
        storeUnder(STORE_KEY, atMs = NOW - THREE_DAYS_MS)
        val store = testedStore(nowMs = NOW)

        store.sweepAbandoned()

        assertThat(store.appliedVersion()).isEqualTo(7)
        assertThat(store.sessionSampleRate()).isEqualTo(42f)
    }

    @Test
    fun `M leave alone every key it did not write W sweepAbandoned()`() {
        preferences.edit().putString("a-key-the-host-app-owns", "not ours").apply()
        storeUnder(OTHER_VERSION_KEY, atMs = NOW - THREE_DAYS_MS)

        testedStore(nowMs = NOW).sweepAbandoned()

        assertThat(preferences.getString("a-key-the-host-app-owns", null)).isEqualTo("not ours")
    }

    @Test
    fun `M take a store key containing separators apart correctly W sweepAbandoned()`() {
        // A service name and an app version both routinely contain dots, so the suffix cannot be
        // found by splitting on the last one.
        val dotted = "${RemoteConfigStore.STORE_KEY_PREFIX}host|app|com.example.shop|prod|1.2.3"
        storeUnder(dotted, atMs = NOW - THREE_DAYS_MS)

        testedStore(nowMs = NOW).sweepAbandoned()

        assertThat(preferences.all.keys.none { it.startsWith(dotted) }).isTrue()
    }

    @Test
    fun `M record when it was written W store()`() {
        val store = testedStore(nowMs = NOW)

        store.store(RemoteConfigValues(42f, 7))

        assertThat(preferences.getLong("$STORE_KEY.writtenAt", 0L)).isEqualTo(NOW)
    }

    @Test
    fun `M refresh the write time without changing the values W touch()`() {
        // The 304 path: the answer is unchanged, so nothing is stored, and age is all the sweep
        // reads. Without this a settled client's entry would never look in use again.
        val store = testedStore(nowMs = NOW - THREE_DAYS_MS)
        store.store(RemoteConfigValues(42f, 7, custom = """{"debug":true}""", etag = "\"v7\""))

        testedStore(nowMs = NOW).touch()

        assertThat(preferences.getLong("$STORE_KEY.writtenAt", 0L)).isEqualTo(NOW)
        assertThat(store.sessionSampleRate()).isEqualTo(42f)
        assertThat(store.appliedVersion()).isEqualTo(7)
        assertThat(store.etag()).isEqualTo("\"v7\"")
    }

    // endregion

    /** Writes a complete entry under [key], stamped as written at [atMs]. */
    private fun storeUnder(key: String, atMs: Long) {
        RemoteConfigStore(appContext, key, mock<InternalLogger>()) { atMs }
            .store(RemoteConfigValues(42f, 7, custom = """{"debug":true}""", etag = "\"v7\""))
    }

    private fun testedStore(nowMs: Long? = null): RemoteConfigStore =
        if (nowMs == null) {
            RemoteConfigStore(appContext, STORE_KEY, mock<InternalLogger>())
        } else {
            RemoteConfigStore(appContext, STORE_KEY, mock<InternalLogger>()) { nowMs }
        }

    private fun datadogContext(): DatadogContext {
        val context = mock<DatadogContext>()
        whenever(context.service).thenReturn(SERVICE)
        whenever(context.env).thenReturn(ENV)
        whenever(context.version).thenReturn(APP_VERSION)
        whenever(context.sdkVersion).thenReturn(SDK_VERSION)
        return context
    }

    /**
     * Just enough of [SharedPreferences] to persist across store instances, which is the whole
     * point of these tests.
     */
    private class InMemorySharedPreferences : SharedPreferences {

        private val values = HashMap<String, Any?>()

        override fun getAll(): Map<String, Any?> = values

        override fun getString(key: String?, defValue: String?): String? =
            values[key] as? String ?: defValue

        @Suppress("OverridingDeprecatedMember")
        override fun getStringSet(key: String?, defValues: Set<String>?): Set<String>? = defValues

        override fun getInt(key: String?, defValue: Int): Int =
            values[key] as? Int ?: defValue

        override fun getLong(key: String?, defValue: Long): Long =
            values[key] as? Long ?: defValue

        override fun getFloat(key: String?, defValue: Float): Float =
            values[key] as? Float ?: defValue

        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            values[key] as? Boolean ?: defValue

        override fun contains(key: String?): Boolean = values.containsKey(key)

        override fun edit(): SharedPreferences.Editor = InMemoryEditor()

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit

        inner class InMemoryEditor : SharedPreferences.Editor {
            override fun putString(key: String?, value: String?) = apply { values[key!!] = value }
            override fun putStringSet(key: String?, value: Set<String>?) = apply { values[key!!] = value }
            override fun putInt(key: String?, value: Int) = apply { values[key!!] = value }
            override fun putLong(key: String?, value: Long) = apply { values[key!!] = value }
            override fun putFloat(key: String?, value: Float) = apply { values[key!!] = value }
            override fun putBoolean(key: String?, value: Boolean) = apply { values[key!!] = value }
            override fun remove(key: String?) = apply { values.remove(key) }
            override fun clear() = apply { values.clear() }
            override fun commit(): Boolean = true
            override fun apply() = Unit
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "flashcat-rum-remote-config"
        private const val SERVICE = "shop-android"
        private const val ENV = "staging"
        private const val APP_VERSION = "1.2.3"
        private const val SDK_VERSION = "9.9.9"

        // Shaped like a real store key, and carrying the prefix, so the sweep considers it at all:
        // a key it skips for the wrong reason would let the "never remove its own entry" test pass
        // with the guard gone.
        private val STORE_KEY = "${RemoteConfigStore.STORE_KEY_PREFIX}host|app|shop|prod|1.0.0"
        private val OTHER_VERSION_KEY = "${RemoteConfigStore.STORE_KEY_PREFIX}host|app|shop|prod|0.9.0"

        private const val NOW = 1_800_000_000_000L
        private const val ONE_DAY_MS = 24L * 60 * 60 * 1000
        private const val THREE_DAYS_MS = 3 * ONE_DAY_MS
    }
}
