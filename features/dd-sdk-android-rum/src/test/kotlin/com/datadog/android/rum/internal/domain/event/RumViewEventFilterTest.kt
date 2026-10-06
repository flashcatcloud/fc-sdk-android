/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event

import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.UUID

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumViewEventFilterTest {

    private lateinit var testedFilter: RumViewEventFilter

    @Mock
    lateinit var mockEventMetaDeserializer: RumEventMetaDeserializer

    @BeforeEach
    fun `set up`() {
        testedFilter = RumViewEventFilter(mockEventMetaDeserializer)
    }

    @Test
    fun `M leave batch untouched W filterOutRedundantViewEvents() { no events with metadata }`(
        forge: Forge
    ) {
        // Given
        val batch = forge.aList { RawBatchEvent(data = aString().toByteArray()) }
        whenever(mockEventMetaDeserializer.deserialize(any())) doReturn null

        // When
        val result = testedFilter.filterOutRedundantViewEvents(batch)

        // Then
        assertThat(result).isEqualTo(batch)
    }

    @Test
    fun `M keep view event with max version W filterOutRedundantViewEvents() { batch of same view event }`(
        @Forgery fakeViewId: UUID,
        forge: Forge
    ) {
        // Given
        val metas = forge.aViewEventMetaList(viewIds = setOf(fakeViewId.toString()))
        val batch = metas.map {
            RawBatchEvent(
                data = forge.aString().toByteArray(),
                metadata = it.toBytes()
            )
        }
        metas.forEach {
            whenever(mockEventMetaDeserializer.deserialize(it.toBytes())) doReturn it
        }

        val expectedViewEvent = batch
            .find { element -> element.metadata.contentEquals(metas.maxBy { it.documentVersion }.toBytes()) }

        // When
        val result = testedFilter.filterOutRedundantViewEvents(batch)

        // Then
        assertThat(result).containsOnly(expectedViewEvent)
    }

    @Test
    fun `M keep view event W hasAccessibility true { regardless of doc version }`(
        forge: Forge
    ) {
        // Given
        val viewEventMetas = forge.aList { getForgery<UUID>().toString() }.toSet()
            .flatMap { forge.aList { getForgery<RumEventMeta.View>().copy(viewId = it, hasAccessibility = true) } }

        val viewEvents = viewEventMetas.map {
            RawBatchEvent(
                data = forge.aString().toByteArray(),
                metadata = it.toBytes()
            )
        }
        val nonViewEvents = forge.aList {
            RawBatchEvent(
                data = forge.aString().toByteArray(),
                metadata = forge.aString().toByteArray()
            )
        }
        val batch = forge.shuffle(nonViewEvents + viewEvents)
        viewEventMetas.forEach {
            whenever(mockEventMetaDeserializer.deserialize(it.toBytes())) doReturn it
        }

        // When
        val result = testedFilter.filterOutRedundantViewEvents(batch)

        // Then
        assertThat(result).containsExactlyElementsOf(batch)
    }

    @Test
    fun `M keep only view event with max doc version W filterOutRedundantViewEvents() { mixed batch }`(
        forge: Forge
    ) {
        // Given
        val viewEventMetas = forge.aViewEventMetaList()
        val viewEvents = viewEventMetas.map {
            RawBatchEvent(
                data = forge.aString().toByteArray(),
                metadata = it.toBytes()
            )
        }
        val nonViewEvents = forge.aList {
            RawBatchEvent(
                data = forge.aString().toByteArray(),
                metadata = forge.aString().toByteArray()
            )
        }
        val batch = forge.shuffle(nonViewEvents + viewEvents)
        viewEventMetas.forEach {
            whenever(mockEventMetaDeserializer.deserialize(it.toBytes())) doReturn it
        }
        val expectedViewMetasToKeep = viewEventMetas.groupBy { it.viewId }
            .mapValues { element -> element.value.maxBy { it.documentVersion } }
            .values
        val expectedViewEventsToDrop = viewEvents
            .filter { viewEvent ->
                expectedViewMetasToKeep.none {
                    it.toBytes().contentEquals(viewEvent.metadata)
                }
            }
        val expectedResult =
            placeAtFirstOccurrence(batch, batch.filter { !expectedViewEventsToDrop.contains(it) }, viewEventMetas)

        // When
        val result = testedFilter.filterOutRedundantViewEvents(batch)

        // Then
        assertThat(result).containsExactlyElementsOf(expectedResult)
    }

    // region accessibility

    @Test
    fun `M keep only max doc version W filterOutRedundantViewEvents() { mixed batch, no accessibility }`(
        forge: Forge
    ) {
        // Given
        val viewEventMetas = forge.aViewEventMetaList(hasAccessibility = false)
        val viewEvents = viewEventMetas.map {
            RawBatchEvent(
                data = forge.aString().toByteArray(),
                metadata = it.toBytes()
            )
        }
        val nonViewEvents = forge.aList {
            RawBatchEvent(
                data = forge.aString().toByteArray(),
                metadata = forge.aString().toByteArray()
            )
        }
        val batch = forge.shuffle(nonViewEvents + viewEvents)
        viewEventMetas.forEach {
            whenever(mockEventMetaDeserializer.deserialize(it.toBytes())) doReturn it
        }
        val expectedViewMetasToKeep = viewEventMetas.groupBy { it.viewId }
            .mapValues { element -> element.value.maxBy { it.documentVersion } }
            .values
        val expectedViewEventsToDrop = viewEvents
            .filter { viewEvent ->
                expectedViewMetasToKeep.none {
                    it.toBytes().contentEquals(viewEvent.metadata)
                }
            }
        val expectedResult =
            placeAtFirstOccurrence(batch, batch.filter { !expectedViewEventsToDrop.contains(it) }, viewEventMetas)

        // When
        val result = testedFilter.filterOutRedundantViewEvents(batch)

        // Then
        assertThat(result).containsExactlyElementsOf(expectedResult)
    }

    @Test
    fun `M all accessibility batches W filterOutRedundantViewEvents() { mixed batch, has accessibility }`(
        forge: Forge
    ) {
        // Given
        val viewEventMetas = forge.aViewEventMetaList(hasAccessibility = true)
        val viewEvents = viewEventMetas.map {
            RawBatchEvent(
                data = forge.aString().toByteArray(),
                metadata = it.toBytes()
            )
        }
        val nonViewEvents = forge.aList {
            RawBatchEvent(
                data = forge.aString().toByteArray(),
                metadata = forge.aString().toByteArray()
            )
        }
        val batch = forge.shuffle(nonViewEvents + viewEvents)
        viewEventMetas.forEach {
            whenever(mockEventMetaDeserializer.deserialize(it.toBytes())) doReturn it
        }

        // When
        val result = testedFilter.filterOutRedundantViewEvents(batch)

        // Then
        assertThat(result).containsExactlyElementsOf(batch)
    }

    @Test
    fun `M keep only accessibility and maxDocVersion W filterOutRedundantViewEvents()`(
        forge: Forge
    ) {
        // Given
        val viewId = forge.aString()
        val newMetaList = mutableListOf<RumEventMeta.View>()
        // max doc version
        val keptMaxDocEvent = RumEventMeta.View(
            viewId = viewId,
            documentVersion = Long.MAX_VALUE,
            hasAccessibility = false
        )
        newMetaList.add(keptMaxDocEvent)

        // accessibility true
        val keptAccessibilityEvent = RumEventMeta.View(
            viewId = viewId,
            documentVersion = Long.MIN_VALUE,
            hasAccessibility = true
        )
        newMetaList.add(keptAccessibilityEvent)

        // some other doc version
        val droppedEvent = RumEventMeta.View(
            viewId = viewId,
            documentVersion = forge.aLong(max = Long.MAX_VALUE - 1),
            hasAccessibility = false
        )
        newMetaList.add(droppedEvent)

        val viewEvents = newMetaList.map {
            RawBatchEvent(
                data = forge.aString().toByteArray(),
                metadata = it.toBytes()
            )
        }
        newMetaList.forEach {
            whenever(mockEventMetaDeserializer.deserialize(it.toBytes())) doReturn it
        }

        val keptMaxDocEventRaw = viewEvents.find { it.metadata.contentEquals(keptMaxDocEvent.toBytes()) }!!
        val keptAccessibilityEventRaw = viewEvents.find {
            it.metadata.contentEquals(
                keptAccessibilityEvent.toBytes()
            )
        }!!
        val droppedEventRaw = viewEvents.find { it.metadata.contentEquals(droppedEvent.toBytes()) }!!

        // When
        val result = testedFilter.filterOutRedundantViewEvents(viewEvents)

        // Then
        assertThat(result).containsExactlyInAnyOrder(keptMaxDocEventRaw, keptAccessibilityEventRaw)
        assertThat(result).doesNotContain(droppedEventRaw)
    }

    // endregion

    // region Internal

    private fun Forge.aViewEventMetaList(
        viewIds: Set<String> = emptySet(),
        hasAccessibility: Boolean = false,
        size: Int = -1
    ): List<RumEventMeta.View> {
        return viewIds.ifEmpty { aList { getForgery<UUID>().toString() }.toSet() }
            .flatMap {
                aList(size = size) {
                    getForgery<RumEventMeta.View>().copy(
                        viewId = it,
                        hasAccessibility = hasAccessibility
                    )
                }
            }
    }

    private fun RumEventMeta.View.toBytes() = toJson().toString().toByteArray()

    // endregion

    @Test
    fun `M keep views ahead of the events they contain W filterOutRedundantViewEvents() { released, then updated }`() {
        // Given - a release writes its views first, oldest first; their live updates follow
        val first = RumEventMeta.View(viewId = "first", documentVersion = 3, hasAccessibility = false)
        val second = RumEventMeta.View(viewId = "second", documentVersion = 2, hasAccessibility = false)
        val firstUpdate = first.copy(documentVersion = 5)
        val secondUpdate = second.copy(documentVersion = 4)
        listOf(first, second, firstUpdate, secondUpdate).forEach {
            whenever(mockEventMetaDeserializer.deserialize(it.toBytes())) doReturn it
        }
        fun event(name: String, meta: RumEventMeta.View? = null) =
            RawBatchEvent(data = name.toByteArray(), metadata = meta?.toBytes() ?: ByteArray(0))
        val batch = listOf(
            event("first", first),
            event("second", second),
            event("error"),
            event("action"),
            event("second+", secondUpdate),
            event("first+", firstUpdate)
        )

        // When
        val result = testedFilter.filterOutRedundantViewEvents(batch)

        // Then
        assertThat(result.map { String(it.data) }).containsExactly("first+", "second+", "error", "action")
    }

    /**
     * The kept events, each kept view moved to where its view first occurs in the batch, and
     * everything else in its own order.
     */
    private fun placeAtFirstOccurrence(
        batch: List<RawBatchEvent>,
        kept: List<RawBatchEvent>,
        metas: List<RumEventMeta.View>
    ): List<RawBatchEvent> {
        fun metaOf(event: RawBatchEvent) = metas.firstOrNull { it.toBytes().contentEquals(event.metadata) }
        val keptByView = kept.filter { metaOf(it)?.hasAccessibility == false }.associateBy { metaOf(it)!!.viewId }
        val placed = mutableSetOf<String>()
        return batch.mapNotNull { event ->
            val meta = metaOf(event)
            when {
                meta == null -> event
                meta.hasAccessibility == true -> event.takeIf { it in kept }
                placed.add(meta.viewId) -> keptByView[meta.viewId]
                else -> null
            }
        }
    }
}
