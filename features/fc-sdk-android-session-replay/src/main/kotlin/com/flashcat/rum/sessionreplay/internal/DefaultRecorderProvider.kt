/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sessionreplay.internal

import android.app.Application
import android.os.Build
import android.webkit.WebView
import android.widget.Button
import android.widget.CheckBox
import android.widget.CheckedTextView
import android.widget.EditText
import android.widget.ImageView
import android.widget.NumberPicker
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.ActionBarContainer
import androidx.appcompat.widget.SwitchCompat
import com.flashcat.rum.api.feature.FeatureSdkCore
import com.flashcat.rum.internal.time.DefaultTimeProvider
import com.flashcat.rum.internal.utils.ImageViewUtils
import com.flashcat.rum.sessionreplay.ImagePrivacy
import com.flashcat.rum.sessionreplay.MapperTypeWrapper
import com.flashcat.rum.sessionreplay.SessionReplayInternalCallback
import com.flashcat.rum.sessionreplay.TextAndInputPrivacy
import com.flashcat.rum.sessionreplay.internal.recorder.Recorder
import com.flashcat.rum.sessionreplay.internal.recorder.SessionReplayRecorder
import com.flashcat.rum.sessionreplay.internal.recorder.mapper.ActionBarContainerMapper
import com.flashcat.rum.sessionreplay.internal.recorder.mapper.ButtonMapper
import com.flashcat.rum.sessionreplay.internal.recorder.mapper.CheckBoxMapper
import com.flashcat.rum.sessionreplay.internal.recorder.mapper.CheckedTextViewMapper
import com.flashcat.rum.sessionreplay.internal.recorder.mapper.NumberPickerMapper
import com.flashcat.rum.sessionreplay.internal.recorder.mapper.ProgressBarWireframeMapper
import com.flashcat.rum.sessionreplay.internal.recorder.mapper.RadioButtonMapper
import com.flashcat.rum.sessionreplay.internal.recorder.mapper.SeekBarWireframeMapper
import com.flashcat.rum.sessionreplay.internal.recorder.mapper.SwitchCompatMapper
import com.flashcat.rum.sessionreplay.internal.recorder.mapper.WebViewWireframeMapper
import com.flashcat.rum.sessionreplay.internal.resources.ResourceDataStoreManager
import com.flashcat.rum.sessionreplay.internal.storage.RecordWriter
import com.flashcat.rum.sessionreplay.internal.storage.ResourcesWriter
import com.flashcat.rum.sessionreplay.internal.utils.RumContextProvider
import com.flashcat.rum.sessionreplay.recorder.OptionSelectorDetector
import com.flashcat.rum.sessionreplay.recorder.mapper.EditTextMapper
import com.flashcat.rum.sessionreplay.recorder.mapper.ImageViewMapper
import com.flashcat.rum.sessionreplay.recorder.mapper.TextViewMapper
import com.flashcat.rum.sessionreplay.recorder.mapper.WireframeMapper
import com.flashcat.rum.sessionreplay.recorder.resources.DefaultDrawableCopier
import com.flashcat.rum.sessionreplay.utils.ColorStringFormatter
import com.flashcat.rum.sessionreplay.utils.DefaultColorStringFormatter
import com.flashcat.rum.sessionreplay.utils.DefaultViewBoundsResolver
import com.flashcat.rum.sessionreplay.utils.DefaultViewIdentifierResolver
import com.flashcat.rum.sessionreplay.utils.DrawableToColorMapper
import com.flashcat.rum.sessionreplay.utils.ViewBoundsResolver
import com.flashcat.rum.sessionreplay.utils.ViewIdentifierResolver

internal class DefaultRecorderProvider(
    private val sdkCore: FeatureSdkCore,
    private val textAndInputPrivacy: TextAndInputPrivacy,
    private val imagePrivacy: ImagePrivacy,
    private val touchPrivacyManager: TouchPrivacyManager,
    private val customMappers: List<MapperTypeWrapper<*>>,
    private val customOptionSelectorDetectors: List<OptionSelectorDetector>,
    private val customDrawableMappers: List<DrawableToColorMapper>,
    private val dynamicOptimizationEnabled: Boolean,
    private val internalCallback: SessionReplayInternalCallback
) : RecorderProvider {

    override fun provideSessionReplayRecorder(
        resourceDataStoreManager: ResourceDataStoreManager,
        resourceWriter: ResourcesWriter,
        recordWriter: RecordWriter,
        rumContextProvider: RumContextProvider,
        application: Application
    ): Recorder {
        return SessionReplayRecorder(
            application,
            resourceDataStoreManager = resourceDataStoreManager,
            resourcesWriter = resourceWriter,
            rumContextProvider = rumContextProvider,
            imagePrivacy = imagePrivacy,
            touchPrivacyManager = touchPrivacyManager,
            textAndInputPrivacy = textAndInputPrivacy,
            recordWriter = recordWriter,
            timeProvider = DefaultTimeProvider(),
            mappers = customMappers + builtInMappers(),
            customOptionSelectorDetectors = customOptionSelectorDetectors,
            customDrawableMappers = customDrawableMappers,
            sdkCore = sdkCore,
            dynamicOptimizationEnabled = dynamicOptimizationEnabled,
            internalCallback = internalCallback
        )
    }

    @Suppress("LongMethod")
    private fun builtInMappers(): List<MapperTypeWrapper<*>> {
        val viewIdentifierResolver: ViewIdentifierResolver = DefaultViewIdentifierResolver
        val colorStringFormatter: ColorStringFormatter = DefaultColorStringFormatter
        val viewBoundsResolver: ViewBoundsResolver = DefaultViewBoundsResolver
        val drawableToColorMapper: DrawableToColorMapper = DrawableToColorMapper.getDefault()
        val imageViewMapper = ImageViewMapper(
            viewIdentifierResolver = viewIdentifierResolver,
            colorStringFormatter = colorStringFormatter,
            viewBoundsResolver = viewBoundsResolver,
            drawableToColorMapper = drawableToColorMapper,
            imageViewUtils = ImageViewUtils,
            drawableCopier = DefaultDrawableCopier()
        )
        val textViewMapper = TextViewMapper<TextView>(
            viewIdentifierResolver,
            colorStringFormatter,
            viewBoundsResolver,
            drawableToColorMapper
        )

        val mappersList = mutableListOf(
            MapperTypeWrapper(
                SwitchCompat::class.java,
                SwitchCompatMapper(
                    textViewMapper as TextViewMapper<SwitchCompat>,
                    viewIdentifierResolver,
                    colorStringFormatter,
                    viewBoundsResolver,
                    drawableToColorMapper
                )
            ),
            MapperTypeWrapper(
                RadioButton::class.java,
                RadioButtonMapper(
                    textViewMapper as TextViewMapper<RadioButton>,
                    viewIdentifierResolver,
                    colorStringFormatter,
                    viewBoundsResolver,
                    drawableToColorMapper,
                    internalLogger = sdkCore.internalLogger
                )
            ),
            MapperTypeWrapper(
                CheckBox::class.java,
                CheckBoxMapper(
                    textViewMapper as TextViewMapper<CheckBox>,
                    viewIdentifierResolver,
                    colorStringFormatter,
                    viewBoundsResolver,
                    drawableToColorMapper,
                    internalLogger = sdkCore.internalLogger
                )
            ),
            MapperTypeWrapper(
                CheckedTextView::class.java,
                CheckedTextViewMapper(
                    textViewMapper as TextViewMapper<CheckedTextView>,
                    viewIdentifierResolver,
                    colorStringFormatter,
                    viewBoundsResolver,
                    drawableToColorMapper
                )
            ),
            MapperTypeWrapper(
                EditText::class.java,
                EditTextMapper(
                    viewIdentifierResolver,
                    colorStringFormatter,
                    viewBoundsResolver,
                    drawableToColorMapper
                )
            ),
            MapperTypeWrapper(
                Button::class.java,
                ButtonMapper(
                    viewIdentifierResolver,
                    colorStringFormatter,
                    viewBoundsResolver,
                    drawableToColorMapper
                )
            ),
            MapperTypeWrapper(
                TextView::class.java,
                textViewMapper
            ),
            MapperTypeWrapper(
                ImageView::class.java,
                imageViewMapper
            ),
            MapperTypeWrapper(
                ActionBarContainer::class.java,
                ActionBarContainerMapper(
                    viewIdentifierResolver,
                    colorStringFormatter,
                    viewBoundsResolver,
                    drawableToColorMapper
                )
            ),
            MapperTypeWrapper(
                WebView::class.java,
                WebViewWireframeMapper(
                    viewIdentifierResolver,
                    colorStringFormatter,
                    viewBoundsResolver,
                    drawableToColorMapper
                )
            ),
            MapperTypeWrapper(
                SeekBar::class.java,
                SeekBarWireframeMapper(
                    viewIdentifierResolver,
                    colorStringFormatter,
                    viewBoundsResolver,
                    drawableToColorMapper
                )
            ),
            MapperTypeWrapper(
                ProgressBar::class.java,
                ProgressBarWireframeMapper(
                    viewIdentifierResolver,
                    colorStringFormatter,
                    viewBoundsResolver,
                    drawableToColorMapper,
                    true
                )
            )
        )

        getNumberPickerMapper(
            viewIdentifierResolver,
            colorStringFormatter,
            viewBoundsResolver,
            drawableToColorMapper
        )?.let {
            mappersList.add(0, MapperTypeWrapper(NumberPicker::class.java, it))
        }
        return mappersList
    }

    private fun getNumberPickerMapper(
        viewIdentifierResolver: ViewIdentifierResolver,
        colorStringFormatter: ColorStringFormatter,
        viewBoundsResolver: ViewBoundsResolver,
        drawableToColorMapper: DrawableToColorMapper
    ): WireframeMapper<NumberPicker>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            NumberPickerMapper(
                viewIdentifierResolver,
                colorStringFormatter,
                viewBoundsResolver,
                drawableToColorMapper
            )
        } else {
            null
        }
    }
}
