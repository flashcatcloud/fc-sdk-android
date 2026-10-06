/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.persistence.Serializer
import com.datadog.android.rum.internal.domain.event.RumEventMeta
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.IdentityHashMap
import java.util.UUID
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class WithheldEventWriterTest {

    private lateinit var testedWriter: WithheldEventWriter

    @Mock
    lateinit var mockSdkCore: InternalSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    private lateinit var forge: Forge

    /** What each event serializes to; an event mapped to null is one a mapper dropped. */
    private val payloads = IdentityHashMap<Any, String?>()

    private val written = mutableListOf<String>()
    private val batchWriter = object : EventBatchWriter {
        override fun currentMetadata(): ByteArray? = null
        override fun write(event: RawBatchEvent, batchMetadata: ByteArray?, eventType: EventType): Boolean {
            written.add(String(event.data, Charsets.UTF_8))
            return true
        }
    }

    private var nowNs = TimeUnit.HOURS.toNanos(1)
    private val scheduled = mutableListOf<Pair<Long, (EventBatchWriter) -> Unit>>()

    private val sessionId = UUID.randomUUID().toString()
    private var nextDate = 1_000L

    @BeforeEach
    fun `set up`(forge: Forge) {
        this.forge = forge
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        val serializer = object : Serializer<Any> {
            override fun serialize(model: Any): String? {
                if (model is ErrorEvent) claimedErrors.add(model)
                if (model is ViewEvent && model.session.hasReplay == true) claimedViews.add(model)
                // A copy claiming the replay serializes like the event it was copied from.
                val key = payloads.keys.firstOrNull { it === model }
                    ?: (model as? ErrorEvent)?.let { error ->
                        payloads.keys.firstOrNull {
                            it == error.copy(session = error.session.copy(hasReplay = null))
                        }
                    }
                    ?: (model as? ViewEvent)?.let { view ->
                        payloads.keys.firstOrNull {
                            it is ViewEvent &&
                                it == view.copy(session = view.session.copy(hasReplay = it.session.hasReplay))
                        }
                    }
                return if (key != null) payloads[key] else "untracked"
            }
        }
        val metaSerializer = object : Serializer<RumEventMeta> {
            override fun serialize(model: RumEventMeta): String = "meta"
        }
        testedWriter = WithheldEventWriter(
            delegate = RumDataWriter(serializer, metaSerializer, mockSdkCore),
            internalLogger = mockInternalLogger,
            elapsedTimeNs = { nowNs },
            scheduleRelease = { delayMs, release -> scheduled.add(delayMs to release) },
            replayRecordsCount = { viewId -> replayRecords[viewId] ?: 0L },
            releaseReplay = { releasedReplays.add(it) },
            discardReplay = { discardedReplays.add(it) },
            expectReplayRelease = { expectedReplays.add(it) }
        )
    }

    private val expectedReplays = mutableListOf<String>()

    @Test
    fun `M tell Session Replay to expect the release W write() {first error}`() {
        // Given
        testedWriter.startWithholding(sessionId, batchWriter)
        testedWriter.write(batchWriter, view("v1"), EventType.DEFAULT)

        // When
        testedWriter.write(batchWriter, error("e1", "v1"), EventType.DEFAULT)
        testedWriter.write(batchWriter, error("e2", "v1"), EventType.DEFAULT)

        // Then - told once, ahead of the release itself
        assertThat(expectedReplays).containsExactly(sessionId)
        assertThat(releasedReplays).isEmpty()
    }

    private val replayRecords = mutableMapOf<String, Long>()
    private val claimedErrors = mutableListOf<ErrorEvent>()
    private val claimedViews = mutableListOf<ViewEvent>()
    private val releasedReplays = mutableListOf<String>()
    private val discardedReplays = mutableListOf<String>()

    // region Withholding

    @Test
    fun `M write straight through W write() {session not withheld}`() {
        // Given
        val view = view("v1", session = UUID.randomUUID().toString())

        // When
        val result = testedWriter.write(batchWriter, view, EventType.DEFAULT)

        // Then
        assertThat(result).isTrue
        assertThat(written).containsExactly("v1")
    }

    @Test
    fun `M write nothing W write() {withheld session, no error}`() {
        // Given
        testedWriter.startWithholding(sessionId, batchWriter)

        // When
        testedWriter.write(batchWriter, view("v1"), EventType.DEFAULT)
        testedWriter.write(batchWriter, action("a1", "v1"), EventType.DEFAULT)
        testedWriter.write(batchWriter, resource("r1", "v1", 200), EventType.DEFAULT)

        // Then
        assertThat(written).isEmpty()
        assertThat(scheduled).isEmpty()
        assertThat(testedWriter.isReleased(sessionId)).isFalse
    }

    @Test
    fun `M discard and drop stragglers W endSession() {no error}`() {
        // Given
        testedWriter.startWithholding(sessionId, batchWriter)
        testedWriter.write(batchWriter, view("v1"), EventType.DEFAULT)

        // When
        testedWriter.endSession(sessionId, batchWriter)
        val straggler = resource("late", "v1", 200)
        val result = testedWriter.write(batchWriter, straggler, EventType.DEFAULT)

        // Then
        assertThat(result).isTrue
        assertThat(written).isEmpty()
    }

    @Test
    fun `M tell Session Replay the session is discarded W endSession() {no error}`() {
        // Given
        testedWriter.startWithholding(sessionId, batchWriter)
        testedWriter.write(batchWriter, view("v1"), EventType.DEFAULT)

        // When
        testedWriter.endSession(sessionId, batchWriter)

        // Then
        assertThat(discardedReplays).containsExactly(sessionId)
        assertThat(releasedReplays).isEmpty()
    }

    @Test
    fun `M forget the view written locally W endSession() {no error}`() {
        // Given
        testedWriter.startWithholding(sessionId, batchWriter)
        testedWriter.write(batchWriter, view("v1"), EventType.DEFAULT)

        // When
        testedWriter.endSession(sessionId, batchWriter)

        // Then
        verify(mockSdkCore).deleteLastViewEvent()
    }

    @Test
    fun `M keep the view written locally W endSession() {session had errored}`() {
        // Given
        testedWriter.startWithholding(sessionId, batchWriter)
        testedWriter.write(batchWriter, view("v1"), EventType.DEFAULT)
        testedWriter.write(batchWriter, error("e1", "v1"), EventType.DEFAULT)

        // When
        testedWriter.endSession(sessionId, batchWriter)

        // Then
        verify(mockSdkCore, never()).deleteLastViewEvent()
    }

    @Test
    fun `M throw the session away and forget its view W stop() {no error}`() {
        // Given
        testedWriter.startWithholding(sessionId, batchWriter)
        testedWriter.write(batchWriter, view("v1"), EventType.DEFAULT)

        // When
        testedWriter.stop(batchWriter)

        // Then
        assertThat(written).isEmpty()
        verify(mockSdkCore).deleteLastViewEvent()
        assertThat(discardedReplays).containsExactly(sessionId)
    }

    @Test
    fun `M release at once W stop() {release waiting for the jitter}`() {
        // Given
        testedWriter.startWithholding(sessionId, batchWriter)
        testedWriter.write(batchWriter, view("v1"), EventType.DEFAULT)
        testedWriter.write(batchWriter, error("e1", "v1"), EventType.DEFAULT)

        // When
        testedWriter.stop(batchWriter)

        // Then
        assertThat(written).containsExactly("v1", "e1")
        verify(mockSdkCore, never()).deleteLastViewEvent()
    }

    @Test
    fun `M tell Session Replay the session is released W endSession() {session had errored}`() {
        // Given
        testedWriter.startWithholding(sessionId, batchWriter)
        testedWriter.write(batchWriter, view("v1"), EventType.DEFAULT)
        testedWriter.write(batchWriter, error("e1", "v1"), EventType.DEFAULT)

        // When
        testedWriter.endSession(sessionId, batchWriter)

        // Then
        assertThat(releasedReplays).containsExactly(sessionId)
        assertThat(discardedReplays).isEmpty()
    }

    @Test
    fun `M write stragglers W endSession() {session had errored}`() {
        // Given
        testedWriter.startWithholding(sessionId, batchWriter)
        testedWriter.write(batchWriter, view("v1"), EventType.DEFAULT)
        testedWriter.write(batchWriter, error("e1", "v1"), EventType.DEFAULT)

        // When
        testedWriter.endSession(sessionId, batchWriter)
        testedWriter.write(batchWriter, resource("late", "v1", 200), EventType.DEFAULT)

        // Then
        assertThat(written).containsExactly("v1", "e1", "late")
    }

    @Test
    fun `M remember only the last sixty-four discarded sessions W endSession()`() {
        // The list covers the writes still in flight behind a session's end; a stopped session's
        // scope feeds nothing more into this writer, see RumSessionScope.
        // Given
        val sessions = List(65) { UUID.randomUUID().toString() }
        sessions.forEach {
            testedWriter.startWithholding(it, batchWriter)
            testedWriter.endSession(it, batchWriter)
        }

        // When
        testedWriter.write(batchWriter, view("first", session = sessions[0]), EventType.DEFAULT)
        testedWriter.write(batchWriter, view("second", session = sessions[1]), EventType.DEFAULT)

        // Then
        assertThat(written).containsExactly("first")
    }

    @Test
    fun `M keep the session withholding W dropHeld()`() {
        // Given
        testedWriter.startWithholding(sessionId, batchWriter)
        testedWriter.write(batchWriter, view("v1"), EventType.DEFAULT)
        testedWriter.write(batchWriter, action("a1", "v1"), EventType.DEFAULT)

        // When
        testedWriter.dropHeld(sessionId)
        testedWriter.write(batchWriter, view("v2"), EventType.DEFAULT)
        testedWriter.write(batchWriter, error("e1", "v2"), EventType.DEFAULT)
        scheduled.single().second(batchWriter)

        // Then
        assertThat(written).containsExactly("v2", "e1")
    }

    @Test
    fun `M write the held view locally W write() {view}`() {
        // Given
        testedWriter.startWithholding(sessionId, batchWriter)

        // When
        testedWriter.write(batchWriter, view("v1"), EventType.DEFAULT)

        // Then
        verify(mockSdkCore).writeLastViewEvent("v1".toByteArray())
        assertThat(written).isEmpty()
    }

    // endregion

    // region Replay

    @Test
    fun `M release the replay only once the events are W write() {error, then jitter}`() {
        // Given
        testedWriter.startWithholding(sessionId, batchWriter)
        testedWriter.write(batchWriter, view("v1"), EventType.DEFAULT)

        // When
        testedWriter.write(batchWriter, error("e1", "v1"), EventType.DEFAULT)
        val beforeJitter = releasedReplays.toList()
        scheduled.single().second(batchWriter)

        // Then
        assertThat(beforeJitter).isEmpty()
        assertThat(releasedReplays).containsExactly(sessionId)
        assertThat(testedWriter.isReplayReleased(sessionId)).isTrue
    }

    @Test
    fun `M claim the replay of released events W release() {view has replay records}`() {
        // Given
        replayRecords["v1"] = 3L
        testedWriter.startWithholding(sessionId, batchWriter)
        testedWriter.write(batchWriter, view("v1", payload = jsonPayload("v1")), EventType.DEFAULT)
        testedWriter.write(batchWriter, action("a1", "v1", payload = jsonPayload("a1")), EventType.DEFAULT)
        testedWriter.write(batchWriter, view("v2", payload = jsonPayload("v2")), EventType.DEFAULT)
        testedWriter.write(batchWriter, action("a2", "v2", payload = jsonPayload("a2")), EventType.DEFAULT)

        // When
        testedWriter.write(batchWriter, error("e1", "v1", payload = jsonPayload("e1")), EventType.DEFAULT)
        scheduled.single().second(batchWriter)

        // Then
        val hasReplay = written.associate {
            val json = com.google.gson.JsonParser.parseString(it).asJsonObject
            json.get("tag").asString to json.getAsJsonObject("session").get("has_replay")?.asBoolean
        }
        assertThat(hasReplay).containsEntry("v1", true).containsEntry("e1", true).containsEntry("a1", true)
        assertThat(hasReplay).containsEntry("v2", null).containsEntry("a2", null)
    }

    @Test
    fun `M claim the replay for the releasing error W write() {watched session, view has records}`() {
        // Given
        replayRecords["v1"] = 2L
        testedWriter.watchForError(sessionId, batchWriter)
        val releasing = error("e1", "v1")

        // When
        testedWriter.write(batchWriter, releasing, EventType.DEFAULT)

        // Then
        assertThat(claimedErrors.single().session.hasReplay).isTrue
        assertThat(releasedReplays).containsExactly(sessionId)
    }

    private fun jsonPayload(tag: String) = "{\"tag\":\"$tag\",\"session\":{\"id\":\"$sessionId\"}}"

    // endregion

    // region Background

    @Test
    fun `M release at once W flushScheduledRelease() {release waiting for the jitter}`() {
        // Given
        testedWriter.startWithholding(sessionId, batchWriter)
        testedWriter.write(batchWriter, view("v1"), EventType.DEFAULT)
        testedWriter.write(batchWriter, error("e1", "v1"), EventType.DEFAULT)

        // When
        testedWriter.flushScheduledRelease(batchWriter)
        scheduled.single().second(batchWriter)

        // Then
        assertThat(written).containsExactly("v1", "e1")
    }

    @Test
    fun `M keep holding W flushScheduledRelease() {no error yet}`() {
        // Given
        testedWriter.startWithholding(sessionId, batchWriter)
        testedWriter.write(batchWriter, view("v1"), EventType.DEFAULT)

        // When
        testedWriter.flushScheduledRelease(batchWriter)
        testedWriter.write(batchWriter, error("e1", "v1"), EventType.DEFAULT)
        scheduled.single().second(batchWriter)

        // Then - still held after the background, so the error that follows has its history
        assertThat(written).containsExactly("v1", "e1")
    }

    // endregion

    // region Details without a held view

    @Test
    fun `M release the error W release() {no view was ever held}`() {
        // Given
        testedWriter.startWithholding(sessionId, batchWriter)
        testedWriter.write(batchWriter, action("a1", "unknown-view"), EventType.DEFAULT)

        // When
        testedWriter.write(batchWriter, error("e1", "unknown-view"), EventType.DEFAULT)
        scheduled.single().second(batchWriter)

        // Then
        assertThat(written).containsExactly("e1", "a1")
    }

    @Test
    fun `M release a detail whose view was evicted W release()`() {
        // Given
        testedWriter.startWithholding(sessionId, batchWriter)
        testedWriter.write(batchWriter, action("a0", "evicted"), EventType.DEFAULT)
        testedWriter.write(batchWriter, view("evicted", date = 0), EventType.DEFAULT)
        testedWriter.write(batchWriter, view("current", date = Long.MAX_VALUE / 2), EventType.DEFAULT)
        repeat(WithheldEventWriter.VIEWS_LIMIT) {
            testedWriter.write(batchWriter, action("x$it", "old$it"), EventType.DEFAULT)
            testedWriter.write(batchWriter, view("old$it", date = it + 1L), EventType.DEFAULT)
        }

        // When
        testedWriter.write(batchWriter, error("e1", "evicted"), EventType.DEFAULT)
        scheduled.single().second(batchWriter)

        // Then
        assertThat(written).doesNotContain("evicted")
        assertThat(written).contains("e1", "a0")
    }

    // endregion

    // region Replay-only sessions

    @Test
    fun `M write through and mark the error W write() {watched session}`() {
        // Given
        testedWriter.watchForError(sessionId, batchWriter)

        // When
        testedWriter.write(batchWriter, view("v1"), EventType.DEFAULT)
        val before = testedWriter.isReplayReleased(sessionId)
        testedWriter.write(batchWriter, error("e1", "v1"), EventType.DEFAULT)

        // Then
        assertThat(before).isFalse
        assertThat(testedWriter.isReplayReleased(sessionId)).isTrue
        assertThat(releasedReplays).containsExactly(sessionId)
        assertThat(written).containsExactly("v1", "e1")
        assertThat(scheduled).isEmpty()
    }

    @Test
    fun `M keep the withheld session released W write() {late error of a watched session}`() {
        // Given - a stopped watched session still drains while the withheld one runs and errors
        val watched = UUID.randomUUID().toString()
        testedWriter.watchForError(watched, batchWriter)
        testedWriter.startWithholding(sessionId, batchWriter)
        testedWriter.write(batchWriter, view("v1"), EventType.DEFAULT)
        testedWriter.write(batchWriter, error("e1", "v1"), EventType.DEFAULT)

        // When
        testedWriter.write(batchWriter, error("late", "v0", session = watched), EventType.DEFAULT)
        testedWriter.endSession(sessionId, batchWriter)

        // Then
        assertThat(testedWriter.isReleased(sessionId)).isTrue
        assertThat(written).containsExactly("late", "v1", "e1")
        assertThat(discardedReplays).doesNotContain(sessionId)
    }

    @Test
    fun `M tell Session Replay the session is discarded W endSession() {watched session, no error}`() {
        // Given
        testedWriter.watchForError(sessionId, batchWriter)
        testedWriter.write(batchWriter, view("v1"), EventType.DEFAULT)

        // When
        testedWriter.endSession(sessionId, batchWriter)

        // Then
        assertThat(discardedReplays).containsExactly(sessionId)
        assertThat(releasedReplays).isEmpty()
    }

    @Test
    fun `M not tell Session Replay twice W endSession() {watched session that errored}`() {
        // Given
        testedWriter.watchForError(sessionId, batchWriter)
        testedWriter.write(batchWriter, error("e1", "v1"), EventType.DEFAULT)

        // When
        testedWriter.endSession(sessionId, batchWriter)

        // Then
        assertThat(releasedReplays).containsExactly(sessionId)
        assertThat(discardedReplays).isEmpty()
    }

    @Test
    fun `M end a watched session left behind W startWithholding()`() {
        // Given
        val watched = UUID.randomUUID().toString()
        testedWriter.watchForError(watched, batchWriter)

        // When
        testedWriter.startWithholding(sessionId, batchWriter)

        // Then
        assertThat(discardedReplays).containsExactly(watched)
    }

    @Test
    fun `M hold nothing more W dropHeldForConsent() {released session, events while consent is withdrawn}`() {
        // Given - the session earned its release, then consent was withdrawn
        testedWriter.startWithholding(sessionId, batchWriter)
        testedWriter.write(batchWriter, view("v1"), EventType.DEFAULT)
        testedWriter.write(batchWriter, error("e1", "v1"), EventType.DEFAULT)
        testedWriter.dropHeldForConsent()

        // When - events of the released session arrive, then consent is granted back
        val result = testedWriter.write(batchWriter, action("a1", "v1"), EventType.DEFAULT)
        scheduled.forEach { it.second(batchWriter) }

        // Then - they went to the batch at once, where consent decides, rather than being held
        assertThat(result).isTrue
        assertThat(written).containsExactly("a1")
        assertThat(scheduled).hasSize(1)
    }

    @Test
    fun `M drop everything held W dropHeldForConsent() {release waiting for the jitter}`() {
        // Given
        testedWriter.startWithholding(sessionId, batchWriter)
        testedWriter.write(batchWriter, view("v1"), EventType.DEFAULT)
        testedWriter.write(batchWriter, error("e1", "v1"), EventType.DEFAULT)

        // When
        testedWriter.dropHeldForConsent()
        scheduled.first().second(batchWriter)
        testedWriter.write(batchWriter, action("a1", "v1"), EventType.DEFAULT)

        // Then - the held minute is gone, the session goes on as a released one
        assertThat(scheduled).hasSize(1)
        assertThat(written).containsExactly("a1")
    }

    @Test
    fun `M claim the replay for a later view W write() {session whose replay was released}`() {
        // Given - the final view of a stopped session is assembled before the replay records land
        testedWriter.startWithholding(sessionId, batchWriter)
        testedWriter.write(batchWriter, view("v1"), EventType.DEFAULT)
        testedWriter.write(batchWriter, error("e1", "v1"), EventType.DEFAULT)
        replayRecords["v1"] = 3L
        testedWriter.endSession(sessionId, batchWriter)

        // When
        val later = view("v1")
        testedWriter.write(batchWriter, later, EventType.DEFAULT)

        // Then
        assertThat(claimedViews.map { it.view.id }).contains("v1")
        assertThat(claimedViews.last().session.hasReplay).isTrue
    }

    @Test
    fun `M not mark W write() {watched session, error dropped by a mapper}`() {
        // Given
        testedWriter.watchForError(sessionId, batchWriter)
        val dropped = error("e1", "v1")
        payloads[dropped] = null

        // When
        testedWriter.write(batchWriter, dropped, EventType.DEFAULT)

        // Then
        assertThat(testedWriter.isReplayReleased(sessionId)).isFalse
        assertThat(releasedReplays).isEmpty()
    }

    // endregion

    // region Release

    @Test
    fun `M release in order after the jitter W write() {error}`() {
        // Given
        testedWriter.startWithholding(sessionId, batchWriter)
        testedWriter.write(batchWriter, view("v1", date = 10), EventType.DEFAULT)
        testedWriter.write(batchWriter, action("a1", "v1"), EventType.DEFAULT)
        testedWriter.write(batchWriter, view("v2", date = 20), EventType.DEFAULT)
        testedWriter.write(batchWriter, resource("r1", "v2", 200), EventType.DEFAULT)
        // a late update of the first view, which must not become current nor go first
        testedWriter.write(batchWriter, view("v1", date = 10, payload = "v1-late"), EventType.DEFAULT)

        // When
        testedWriter.write(batchWriter, error("e1", "v2"), EventType.DEFAULT)

        // Then
        assertThat(written).isEmpty()
        assertThat(testedWriter.isReleased(sessionId)).isTrue
        assertThat(scheduled).hasSize(1)
        assertThat(scheduled.single().first).isEqualTo(WithheldEventWriter.computeReleaseDelayMs(sessionId))

        // When
        testedWriter.write(batchWriter, action("a2", "v2"), EventType.DEFAULT)
        scheduled.single().second(batchWriter)
        testedWriter.write(batchWriter, action("a3", "v2"), EventType.DEFAULT)

        // Then
        assertThat(written).containsExactly("v1-late", "v2", "e1", "a1", "r1", "a2", "a3")
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.INFO),
            eq(InternalLogger.Target.TELEMETRY),
            any(),
            anyOrNull(),
            eq(false),
            eq(
                mapOf<String, Any>(
                    "buffer.views_count" to 2,
                    "buffer.events_count" to 4,
                    "buffer.dropped_count" to 0,
                    "buffer.bytes" to 8L
                )
            )
        )
    }

    @Test
    fun `M release nothing W write() {error dropped by a mapper}`() {
        // Given
        testedWriter.startWithholding(sessionId, batchWriter)
        testedWriter.write(batchWriter, view("v1"), EventType.DEFAULT)
        val dropped = error("e1", "v1")
        payloads[dropped] = null

        // When
        val result = testedWriter.write(batchWriter, dropped, EventType.DEFAULT)

        // Then
        assertThat(result).isFalse
        assertThat(scheduled).isEmpty()
        assertThat(testedWriter.isReleased(sessionId)).isFalse
        assertThat(written).isEmpty()
    }

    @Test
    fun `M release nothing W write() {error of another session}`() {
        // Given
        testedWriter.startWithholding(sessionId, batchWriter)
        testedWriter.write(batchWriter, view("v1"), EventType.DEFAULT)

        // When
        testedWriter.write(batchWriter, error("e1", "v1", session = UUID.randomUUID().toString()), EventType.DEFAULT)

        // Then
        assertThat(scheduled).isEmpty()
        assertThat(written).containsExactly("e1")
    }

    @Test
    fun `M release at once without jitter W write() {crash}`() {
        // Given
        testedWriter.startWithholding(sessionId, batchWriter)
        testedWriter.write(batchWriter, view("v1"), EventType.DEFAULT)
        testedWriter.write(batchWriter, action("a1", "v1"), EventType.DEFAULT)

        // When
        testedWriter.write(batchWriter, error("crash", "v1", isCrash = true), EventType.CRASH)

        // Then
        assertThat(scheduled).isEmpty()
        assertThat(written).containsExactly("v1", "crash", "a1")
    }

    @Test
    fun `M release at once W write() {crash while a release waits for the jitter}`() {
        // Given
        testedWriter.startWithholding(sessionId, batchWriter)
        testedWriter.write(batchWriter, view("v1"), EventType.DEFAULT)
        testedWriter.write(batchWriter, error("e1", "v1"), EventType.DEFAULT)

        // When
        testedWriter.write(batchWriter, error("crash", "v1", isCrash = true), EventType.CRASH)
        scheduled.single().second(batchWriter)

        // Then
        assertThat(written).containsExactly("v1", "e1", "crash")
    }

    @Test
    fun `M release at once W endSession() {release waiting for the jitter}`() {
        // Given
        testedWriter.startWithholding(sessionId, batchWriter)
        testedWriter.write(batchWriter, view("v1"), EventType.DEFAULT)
        testedWriter.write(batchWriter, error("e1", "v1"), EventType.DEFAULT)

        // When
        testedWriter.endSession(sessionId, batchWriter)
        scheduled.single().second(batchWriter)

        // Then
        assertThat(written).containsExactly("v1", "e1")
    }

    @Test
    fun `M release at once W forceRelease()`() {
        // Given
        testedWriter.startWithholding(sessionId, batchWriter)
        testedWriter.write(batchWriter, view("v1"), EventType.DEFAULT)
        testedWriter.write(batchWriter, action("a1", "v1"), EventType.DEFAULT)

        // When
        testedWriter.forceRelease(sessionId, batchWriter)
        testedWriter.write(batchWriter, action("a2", "v1"), EventType.DEFAULT)

        // Then
        assertThat(scheduled).isEmpty()
        assertThat(testedWriter.isReleased(sessionId)).isTrue
        assertThat(written).containsExactly("v1", "a1", "a2")
    }

    @Test
    fun `M release the previous session W startWithholding() {previous one errored}`() {
        // Given
        testedWriter.startWithholding(sessionId, batchWriter)
        testedWriter.write(batchWriter, view("v1"), EventType.DEFAULT)
        testedWriter.write(batchWriter, error("e1", "v1"), EventType.DEFAULT)

        // When
        val nextSessionId = UUID.randomUUID().toString()
        testedWriter.startWithholding(nextSessionId, batchWriter)
        testedWriter.write(batchWriter, view("v2", session = nextSessionId), EventType.DEFAULT)

        // Then
        assertThat(written).containsExactly("v1", "e1")
    }

    @Test
    fun `M never evict the crash W write() {crash and an earlier error over the budget together}`() {
        // Given - each fits the budget on its own, both do not
        val chunk = WithheldEventWriter.BYTES_LIMIT * 2 / 3
        testedWriter.startWithholding(sessionId, batchWriter)
        testedWriter.write(batchWriter, view("v1"), EventType.DEFAULT)
        testedWriter.write(batchWriter, error("e1", "v1", payload = "1".repeat(chunk)), EventType.DEFAULT)

        // When
        testedWriter.write(
            batchWriter,
            error("c1", "v1", isCrash = true, payload = "C".repeat(chunk)),
            EventType.CRASH
        )

        // Then - the earlier error made room for the crash, not the other way round
        assertThat(written.map { it.first() }).containsExactly('v', 'C')
    }

    @Test
    fun `M release the history at once W write() {oversized crash}`() {
        // Given
        testedWriter.startWithholding(sessionId, batchWriter)
        testedWriter.write(batchWriter, view("v1"), EventType.DEFAULT)
        testedWriter.write(batchWriter, action("a1", "v1"), EventType.DEFAULT)
        val hugeCrash = error("c1", "v1", isCrash = true, payload = "C".repeat(WithheldEventWriter.BYTES_LIMIT + 1))

        // When
        testedWriter.write(batchWriter, hugeCrash, EventType.CRASH)

        // Then
        assertThat(scheduled).isEmpty()
        assertThat(written.drop(1)).containsExactly("v1", "a1")
    }

    @Test
    fun `M send an oversized error on its own W write() {error over the budget}`() {
        // Given
        testedWriter.startWithholding(sessionId, batchWriter)
        testedWriter.write(batchWriter, view("v1"), EventType.DEFAULT)
        testedWriter.write(batchWriter, action("a1", "v1"), EventType.DEFAULT)
        val hugeError = error("e1", "v1", payload = "E".repeat(WithheldEventWriter.BYTES_LIMIT + 1))

        // When
        testedWriter.write(batchWriter, hugeError, EventType.DEFAULT)

        // Then
        assertThat(written).hasSize(1)
        assertThat(written.single()).startsWith("EEE")
        assertThat(scheduled).hasSize(1)

        // When
        scheduled.single().second(batchWriter)

        // Then
        assertThat(written.drop(1)).containsExactly("v1", "a1")
    }

    @Test
    fun `M drop an oversized event and keep the history W write() {non-error over the budget}`() {
        // Given
        testedWriter.startWithholding(sessionId, batchWriter)
        testedWriter.write(batchWriter, view("v1"), EventType.DEFAULT)
        testedWriter.write(batchWriter, action("a1", "v1"), EventType.DEFAULT)

        // When
        testedWriter.write(
            batchWriter,
            action("huge", "v1", payload = "A".repeat(WithheldEventWriter.BYTES_LIMIT + 1)),
            EventType.DEFAULT
        )
        testedWriter.write(batchWriter, error("e1", "v1"), EventType.DEFAULT)
        scheduled.single().second(batchWriter)

        // Then
        assertThat(written).containsExactly("v1", "e1", "a1")
    }

    // endregion

    // region Window and budget

    @Test
    fun `M keep only the last minute W release()`() {
        // Given
        testedWriter.startWithholding(sessionId, batchWriter)
        testedWriter.write(batchWriter, view("v1"), EventType.DEFAULT)
        testedWriter.write(batchWriter, action("old", "v1"), EventType.DEFAULT)
        nowNs += TimeUnit.SECONDS.toNanos(30)
        testedWriter.write(batchWriter, action("recent", "v1"), EventType.DEFAULT)
        nowNs += TimeUnit.SECONDS.toNanos(31)

        // When
        testedWriter.write(batchWriter, error("e1", "v1"), EventType.DEFAULT)
        scheduled.single().second(batchWriter)

        // Then
        assertThat(written).containsExactly("v1", "e1", "recent")
    }

    @Test
    fun `M freeze the window when the release is scheduled W release() {timer late}`() {
        // Given
        testedWriter.startWithholding(sessionId, batchWriter)
        testedWriter.write(batchWriter, view("v1"), EventType.DEFAULT)
        testedWriter.write(batchWriter, action("a1", "v1"), EventType.DEFAULT)
        nowNs += TimeUnit.SECONDS.toNanos(50)
        testedWriter.write(batchWriter, error("e1", "v1"), EventType.DEFAULT)

        // When
        nowNs += TimeUnit.MINUTES.toNanos(2)
        scheduled.single().second(batchWriter)

        // Then
        assertThat(written).containsExactly("v1", "e1", "a1")
    }

    @Test
    fun `M drop views left with nothing in the window W release()`() {
        // Given
        testedWriter.startWithholding(sessionId, batchWriter)
        testedWriter.write(batchWriter, view("v1", date = 1), EventType.DEFAULT)
        testedWriter.write(batchWriter, action("a1", "v1"), EventType.DEFAULT)
        nowNs += TimeUnit.SECONDS.toNanos(61)
        testedWriter.write(batchWriter, view("v2", date = 2), EventType.DEFAULT)

        // When
        testedWriter.write(batchWriter, error("e1", "v2"), EventType.DEFAULT)
        scheduled.single().second(batchWriter)

        // Then
        assertThat(written).containsExactly("v2", "e1")
    }

    @Test
    fun `M evict successful requests and long tasks first W write() {over the event count}`() {
        // Given
        testedWriter.startWithholding(sessionId, batchWriter)
        testedWriter.write(batchWriter, view("v1"), EventType.DEFAULT)
        testedWriter.write(batchWriter, resource("ok", "v1", 200), EventType.DEFAULT)
        testedWriter.write(batchWriter, longTask("lt", "v1"), EventType.DEFAULT)
        testedWriter.write(batchWriter, resource("failed", "v1", 500), EventType.DEFAULT)
        repeat(WithheldEventWriter.EVENTS_LIMIT - 3) {
            testedWriter.write(batchWriter, action("a$it", "v1"), EventType.DEFAULT)
        }

        // When
        testedWriter.write(batchWriter, error("e1", "v1"), EventType.DEFAULT)
        testedWriter.write(batchWriter, action("last", "v1"), EventType.DEFAULT)
        scheduled.single().second(batchWriter)

        // Then
        assertThat(written).doesNotContain("ok", "lt")
        assertThat(written).contains("failed", "last")
        assertThat(written.take(2)).containsExactly("v1", "e1")
        assertThat(written).hasSize(1 + WithheldEventWriter.EVENTS_LIMIT)
    }

    @Test
    fun `M evict by bytes keeping errors W write() {over the byte budget}`() {
        // Given
        val chunk = WithheldEventWriter.BYTES_LIMIT / 4 + 1
        testedWriter.startWithholding(sessionId, batchWriter)
        testedWriter.write(batchWriter, view("v1"), EventType.DEFAULT)
        testedWriter.write(batchWriter, resource("ok", "v1", 204, payload = "R".repeat(chunk)), EventType.DEFAULT)
        testedWriter.write(batchWriter, error("e1", "v1", payload = "1".repeat(chunk)), EventType.DEFAULT)
        testedWriter.write(batchWriter, action("act", "v1", payload = "A".repeat(chunk)), EventType.DEFAULT)

        // When
        testedWriter.write(batchWriter, error("e2", "v1", payload = "2".repeat(chunk)), EventType.DEFAULT)
        scheduled.single().second(batchWriter)

        // Then
        assertThat(written.map { it.first() }).containsExactly('v', '1', '2', 'A')
    }

    @Test
    fun `M evict the newest error first W write() {only errors left}`() {
        // Given
        val chunk = WithheldEventWriter.BYTES_LIMIT / 3
        testedWriter.startWithholding(sessionId, batchWriter)
        testedWriter.write(batchWriter, view("v1"), EventType.DEFAULT)
        testedWriter.write(batchWriter, error("e1", "v1", payload = "1".repeat(chunk)), EventType.DEFAULT)
        testedWriter.write(batchWriter, error("e2", "v1", payload = "2".repeat(chunk)), EventType.DEFAULT)
        testedWriter.write(batchWriter, error("e3", "v1", payload = "3".repeat(chunk)), EventType.DEFAULT)

        // When
        testedWriter.write(batchWriter, error("e4", "v1", payload = "4".repeat(chunk)), EventType.DEFAULT)
        scheduled.single().second(batchWriter)

        // Then
        assertThat(written.map { it.first() }).containsExactly('v', '1', '2', '3')
    }

    @Test
    fun `M never evict the current view W write() {over the view count}`() {
        // Given
        testedWriter.startWithholding(sessionId, batchWriter)
        testedWriter.write(batchWriter, view("current", date = Long.MAX_VALUE / 2), EventType.DEFAULT)
        testedWriter.write(batchWriter, action("a0", "current"), EventType.DEFAULT)
        repeat(WithheldEventWriter.VIEWS_LIMIT + 5) {
            // ended views updated late, each with something in the window
            testedWriter.write(batchWriter, action("a${it + 1}", "old$it"), EventType.DEFAULT)
            testedWriter.write(batchWriter, view("old$it", date = it.toLong()), EventType.DEFAULT)
        }

        // When
        testedWriter.write(batchWriter, error("e1", "current"), EventType.DEFAULT)
        scheduled.single().second(batchWriter)

        // Then
        val views = written.filter { it == "current" || it.startsWith("old") }
        assertThat(views).hasSize(WithheldEventWriter.VIEWS_LIMIT)
        assertThat(views.last()).isEqualTo("current")
        assertThat(written).contains("e1", "a0")
        // the evicted view is gone; its detail still goes, views only order the release
        assertThat(written).doesNotContain("old0")
        assertThat(written).contains("a1")
    }

    // endregion

    @Test
    fun `M spread releases over the whole window W computeReleaseDelayMs()`() {
        // When
        val delays = List(2_000) { WithheldEventWriter.computeReleaseDelayMs(UUID.randomUUID().toString()) }

        // Then
        assertThat(delays).allMatch { it in 0 until WithheldEventWriter.RELEASE_MAX_DELAY_MS }
        val buckets = delays.groupBy { it / 300 }
        assertThat(buckets.keys).containsExactlyInAnyOrder(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L)
        assertThat(buckets.values.map { it.size }).allMatch { it in 100..300 }
        assertThat(WithheldEventWriter.computeReleaseDelayMs(sessionId))
            .isEqualTo(WithheldEventWriter.computeReleaseDelayMs(sessionId))
    }

    @Test
    fun `M not write the last view W release()`() {
        // Given
        testedWriter.startWithholding(sessionId, batchWriter)
        testedWriter.write(batchWriter, view("v1"), EventType.DEFAULT)
        testedWriter.write(batchWriter, error("e1", "v1"), EventType.DEFAULT)

        // When
        scheduled.single().second(batchWriter)

        // Then
        val captor = argumentCaptor<ByteArray>()
        verify(mockSdkCore).writeLastViewEvent(captor.capture())
        assertThat(captor.allValues).hasSize(1)
        verify(mockSdkCore, never()).writeLastViewEvent(eq("e1".toByteArray()))
    }

    // region Helpers

    private fun view(
        id: String,
        session: String = sessionId,
        date: Long = nextDate++,
        payload: String = id
    ): ViewEvent {
        val base = forge.getForgery(ViewEvent::class.java)
        return base.copy(
            date = date,
            session = base.session.copy(id = session),
            view = base.view.copy(id = id)
        ).also { payloads[it] = payload }
    }

    private fun action(name: String, viewId: String, payload: String = name): ActionEvent {
        val base = forge.getForgery(ActionEvent::class.java)
        return base.copy(
            session = base.session.copy(id = sessionId),
            view = base.view.copy(id = viewId)
        ).also { payloads[it] = payload }
    }

    private fun resource(name: String, viewId: String, status: Long, payload: String = name): ResourceEvent {
        val base = forge.getForgery(ResourceEvent::class.java)
        return base.copy(
            session = base.session.copy(id = sessionId),
            view = base.view.copy(id = viewId),
            resource = base.resource.copy(statusCode = status)
        ).also { payloads[it] = payload }
    }

    private fun longTask(name: String, viewId: String): LongTaskEvent {
        val base = forge.getForgery(LongTaskEvent::class.java)
        return base.copy(
            session = base.session.copy(id = sessionId),
            view = base.view.copy(id = viewId)
        ).also { payloads[it] = name }
    }

    private fun error(
        name: String,
        viewId: String,
        session: String = sessionId,
        isCrash: Boolean = false,
        payload: String = name
    ): ErrorEvent {
        val base = forge.getForgery(ErrorEvent::class.java)
        return base.copy(
            session = base.session.copy(id = session),
            view = base.view.copy(id = viewId),
            error = base.error.copy(isCrash = isCrash)
        ).also { payloads[it] = payload }
    }

    // endregion
}
