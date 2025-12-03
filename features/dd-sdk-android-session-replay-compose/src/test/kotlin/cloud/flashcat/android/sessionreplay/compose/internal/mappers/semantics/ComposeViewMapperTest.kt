/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.sessionreplay.compose.internal.mappers.semantics

import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import cloud.flashcat.android.api.InternalLogger
import cloud.flashcat.android.sessionreplay.compose.internal.utils.SemanticsUtils
import cloud.flashcat.android.sessionreplay.compose.test.elmyr.SessionReplayComposeForgeConfigurator
import cloud.flashcat.android.sessionreplay.recorder.MappingContext
import cloud.flashcat.android.sessionreplay.utils.AsyncJobStatusCallback
import cloud.flashcat.android.sessionreplay.utils.ColorStringFormatter
import cloud.flashcat.android.sessionreplay.utils.DrawableToColorMapper
import cloud.flashcat.android.sessionreplay.utils.ViewBoundsResolver
import cloud.flashcat.android.sessionreplay.utils.ViewIdentifierResolver
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(SessionReplayComposeForgeConfigurator::class)
class ComposeViewMapperTest {

    @Mock
    private lateinit var mockRootSemanticsNodeMapper: RootSemanticsNodeMapper

    @Mock
    private lateinit var mockViewIdentifierResolver: ViewIdentifierResolver

    @Mock
    private lateinit var mockColorStringFormatter: ColorStringFormatter

    @Mock
    private lateinit var mockViewBoundsResolver: ViewBoundsResolver

    @Mock
    private lateinit var mockDrawableToColorMapper: DrawableToColorMapper

    @Mock
    private lateinit var mockView: ComposeView

    @Mock
    private lateinit var mockAsyncJobStatusCallback: AsyncJobStatusCallback

    @Mock
    private lateinit var mockInternalLogger: InternalLogger

    @Mock
    private lateinit var mockSemanticsUtils: SemanticsUtils

    @Mock
    private lateinit var mockSemanticsConfiguration: SemanticsConfiguration

    @Forgery
    private lateinit var fakeMappingContext: MappingContext

    private lateinit var testedComposeViewMapper: ComposeViewMapper

    @BeforeEach
    fun `set up`() {
        testedComposeViewMapper = ComposeViewMapper(
            mockViewIdentifierResolver,
            mockColorStringFormatter,
            mockViewBoundsResolver,
            mockDrawableToColorMapper,
            mockSemanticsUtils,
            mockRootSemanticsNodeMapper
        )
    }

    @Test
    fun `M invoke rootSemanticsNodeMapper createComposeWireframes W map`() {
        // Given
        val mockSemanticsNode = mockSemanticsNode(null)
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(mockSemanticsNode)

        // When
        testedComposeViewMapper.map(
            mockView,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        verify(mockRootSemanticsNodeMapper).createComposeWireframes(
            mockSemanticsNode,
            fakeMappingContext.systemInformation.screenDensity,
            fakeMappingContext,
            mockAsyncJobStatusCallback
        )
    }

    private fun mockSemanticsNode(role: Role?): SemanticsNode {
        return mock {
            whenever(mockSemanticsConfiguration.getOrNull(SemanticsProperties.Role)) doReturn role
            whenever(it.config) doReturn mockSemanticsConfiguration
        }
    }
}
