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
import java.util.concurrent.TimeUnit

/**
 * Holds the remote configuration the console last sent for this application.
 *
 * They live on disk rather than in memory so a rate fetched during one launch already applies to
 * the first session of the next one, instead of every cold start beginning on the rates the app was
 * built with and only correcting itself once a request comes back.
 *
 * A rate the console did not send is absent here, never zero: the caller falls back to the value
 * passed to the SDK at init. Inventing a zero would silently stop collection nobody asked to stop.
 */
internal class RemoteConfigStore(
    appContext: Context,
    private val storeKey: String,
    private val internalLogger: InternalLogger,
    /**
     * Wall clock, deliberately not [android.os.SystemClock.elapsedRealtime]: an entry's age has to
     * survive the process ending and the device rebooting, which is exactly what an elapsed-time
     * clock forgets.
     *
     * A wall clock can be moved, and that is the accepted cost. Moved back, an abandoned entry
     * looks younger and is swept later; moved forward, a live one may be swept early and its next
     * session runs on the init values before it is written back. Both recover on their own.
     */
    private val currentTimeMs: () -> Long = System::currentTimeMillis
) {

    private val preferences: SharedPreferences? = try {
        appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    } catch (e: SecurityException) {
        internalLogger.log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            { STORAGE_UNAVAILABLE_MESSAGE },
            e
        )
        null
    }

    fun sessionSampleRate(): Float? = read(sessionKey())

    /**
     * The application-defined bag the console last published, as the raw JSON object string, or
     * null when none is published. The platform never interprets it — see [RumMonitor.getRemoteConfig].
     */
    fun custom(): String? = preferences?.getString(customKey(), null)

    /**
     * The validator the server sent with the stored configuration, echoed back as If-None-Match so
     * an unchanged answer costs a 304 instead of a body. It belongs to this stored configuration
     * specifically: the answer varies per caller, so it cannot be shared or guessed.
     */
    fun etag(): String? = preferences?.getString(etagKey(), null)

    /**
     * Which version of the settings the stored rates came from, or null before the first answer.
     * Reported back on the next request so the console can say how far a change has reached — a
     * question the events cannot answer, because a session that was not kept sends none, and the
     * miss rate is set by the very rate being changed.
     */
    fun appliedVersion(): Int? {
        val stored = preferences?.getInt(versionKey(), ABSENT_VERSION) ?: ABSENT_VERSION
        return if (stored == ABSENT_VERSION) null else stored
    }

    /**
     * Replaces what is stored with what the response carried. Rates the response omitted are
     * removed rather than left behind, so switching a knob off in the console really does hand that
     * knob back to the value the app was initialised with.
     */
    fun store(values: RemoteConfigValues) {
        val editor = preferences?.edit() ?: return
        write(editor, sessionKey(), values.sessionSampleRate)
        // Kept even when there are no rates — that is what "remote configuration is off, use your
        // own settings" looks like — so the console can still see this client is up to date with
        // the change that turned them off.
        if (values.version == null) {
            editor.remove(versionKey())
        } else {
            editor.putInt(versionKey(), values.version)
        }
        if (values.custom == null) {
            editor.remove(customKey())
        } else {
            editor.putString(customKey(), values.custom)
        }
        if (values.etag == null) {
            editor.remove(etagKey())
        } else {
            editor.putString(etagKey(), values.etag)
        }
        editor.putLong(writeTimeKey(), currentTimeMs())
        editor.apply()
    }

    /**
     * Records that this entry is still in use, without changing what it holds.
     *
     * There is one way to reach an entry and store nothing: an unchanged answer comes back as a
     * 304 with no body. A client that has settled — the common case, since most fetches find the
     * configuration unchanged — would otherwise never refresh its entry's age again, and two SDK
     * instances in one app, each sweeping on behalf of its own key, would end up deleting each
     * other's settled entry at every launch.
     *
     * Nothing guards against there being no entry: a 304 can only answer a request that carried an
     * If-None-Match, which can only have come from a stored validator.
     */
    fun touch() {
        preferences?.edit()?.putLong(writeTimeKey(), currentTimeMs())?.apply()
    }

    /**
     * Removes the entries of app versions this device is no longer running.
     *
     * The key covers the app version, so every release the device installs leaves one behind, and
     * nothing read or removed them again — they accumulated for good inside a preferences file
     * that is parsed in full at every launch. Age is what separates an abandoned entry from a live
     * one: a store that is still being read is also being written, at the latest by [touch] when
     * its answer comes back unchanged.
     *
     * This store's own entry is never a candidate, whatever its age says. It is the one entry that
     * is certainly in use — the caller is about to read it — and on a first launch it has no write
     * time yet at all.
     */
    fun sweepAbandoned() {
        val preferences = this.preferences ?: return
        val now = currentTimeMs()

        // Read once, into keys of our own, before anything is removed: the map a preferences
        // implementation hands back may be its live one, and editing while walking it would be
        // undefined.
        val stored = preferences.all
        val entryKeys = stored.keys.mapNotNull(::entryKeyOf).toSet()

        val editor = preferences.edit()
        var abandoned = false
        for (entryKey in entryKeys) {
            if (entryKey == storeKey) continue
            // Absent, or holding something we did not write, reads as older than any threshold.
            val writtenAtMs = stored["$entryKey$SUFFIX_WRITE_TIME"] as? Long ?: NEVER_WRITTEN
            if (now - writtenAtMs <= MAX_ENTRY_AGE_MS) continue
            FIELD_SUFFIXES.forEach { editor.remove("$entryKey$it") }
            abandoned = true
        }
        if (abandoned) editor.apply()
    }

    /**
     * The entry a stored key belongs to, or null when the key is not one of ours. Splitting on the
     * known suffixes rather than on the last separator is what keeps a store key free to contain
     * one: a service name and an app version both routinely do.
     */
    private fun entryKeyOf(key: String): String? {
        if (!key.startsWith(STORE_KEY_PREFIX)) return null
        val suffix = FIELD_SUFFIXES.firstOrNull { key.endsWith(it) } ?: return null
        return key.removeSuffix(suffix)
    }

    private fun read(key: String): Float? {
        val stored = preferences?.getFloat(key, ABSENT) ?: ABSENT
        return if (stored == ABSENT) null else stored
    }

    private fun write(editor: SharedPreferences.Editor, key: String, rate: Float?) {
        if (rate == null) {
            editor.remove(key)
        } else {
            editor.putFloat(key, rate)
        }
    }

    private fun sessionKey() = "$storeKey$SUFFIX_SESSION_SAMPLE_RATE"

    private fun versionKey() = "$storeKey$SUFFIX_VERSION"

    private fun customKey() = "$storeKey$SUFFIX_CUSTOM"

    private fun etagKey() = "$storeKey$SUFFIX_ETAG"

    private fun writeTimeKey() = "$storeKey$SUFFIX_WRITE_TIME"


    companion object {
        private const val PREFERENCES_NAME = "flashcat-rum-remote-config"

        /**
         * The `1` is the storage format version, not the SDK version: it changes only when the
         * shape of what we store changes, so an SDK upgrade keeps the cache (losing it would put
         * the first session after every upgrade back on the init values), while a format change
         * orphans the old entry instead of asking new code to parse it.
         */
        internal const val STORE_KEY_PREFIX = "_fc_rc_1_"

        // SharedPreferences has no "absent" for a primitive read, and every legitimate rate is
        // within 0..100, so a negative sentinel can never collide with a stored value.
        private const val ABSENT = -1f
        private const val ABSENT_VERSION = -1

        // One entry is spread over several keys, all of them derived from the store key by these
        // suffixes. Named here once because two things read them: the accessors that build a key,
        // and the sweep that has to take an entry apart again.
        private const val SUFFIX_SESSION_SAMPLE_RATE = ".sessionSampleRate"
        private const val SUFFIX_VERSION = ".version"
        private const val SUFFIX_CUSTOM = ".custom"
        private const val SUFFIX_ETAG = ".etag"
        private const val SUFFIX_WRITE_TIME = ".writtenAt"

        private val FIELD_SUFFIXES = listOf(
            SUFFIX_SESSION_SAMPLE_RATE,
            SUFFIX_VERSION,
            SUFFIX_CUSTOM,
            SUFFIX_ETAG,
            SUFFIX_WRITE_TIME
        )

        /**
         * How long an entry may go unrefreshed before [sweepAbandoned] takes it for the entry of a
         * version this device no longer runs.
         *
         * The threshold has to clear the longest a live entry can legitimately stay silent: the
         * longest session (four hours — see `RumSessionScope.DEFAULT_SESSION_MAX_DURATION_NS`,
         * after which a new session fetches again) plus the longest endpoint outage worth
         * surviving, since a failed fetch stores nothing. Two days leaves better than a day and a
         * half of outage.
         *
         * Erring long is deliberate: sweeping an entry too early costs the next session its
         * remote rates, keeping a dead one costs a few hundred bytes.
         */
        private val MAX_ENTRY_AGE_MS = TimeUnit.DAYS.toMillis(2)

        // Older than any threshold without reaching for a sentinel that could underflow the
        // subtraction: an entry stamped at the epoch is abandoned by any reading.
        private const val NEVER_WRITTEN = 0L

        internal const val STORAGE_UNAVAILABLE_MESSAGE =
            "Unable to open the remote configuration store; the values passed to init will apply."

        /**
         * Identifies whose configuration this is. It covers everything that can change the answer —
         * which endpoint the app asks, which application, in which environment, at which app
         * version — so an app that ships a new version, or two applications sharing a device, never
         * read each other's values.
         *
         * It deliberately leaves out the SDK version: including it would discard the stored values
         * on every SDK upgrade and put the first session after an upgrade back on the init values.
         * The storage format version lives in [STORE_KEY_PREFIX] instead, so only a real format
         * change orphans the cache.
         */
        fun buildStoreKey(context: DatadogContext, intakeUrl: String, applicationId: String): String {
            val host = try {
                @Suppress("UnsafeThirdPartyFunctionCall") // caught right below
                java.net.URI(intakeUrl).host ?: intakeUrl
            } catch (e: IllegalArgumentException) {
                intakeUrl
            } catch (e: java.net.URISyntaxException) {
                intakeUrl
            }
            return STORE_KEY_PREFIX + listOf(
                host,
                applicationId,
                context.service,
                context.env,
                context.version
            ).joinToString("|")
        }
    }
}

/**
 * The values carried by one configuration response. Null means the console did not set that knob.
 */
internal data class RemoteConfigValues(
    val sessionSampleRate: Float?,
    val version: Int? = null,
    /** Raw JSON object string of the console's custom pass-through values, delivered verbatim. */
    val custom: String? = null,
    /** The validator to echo back as If-None-Match on the next request, quoted as the server sent it. */
    val etag: String? = null
)
