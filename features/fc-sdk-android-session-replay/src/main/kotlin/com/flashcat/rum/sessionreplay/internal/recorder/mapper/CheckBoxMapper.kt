/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sessionreplay.internal.recorder.mapper

import android.widget.CheckBox
import com.flashcat.rum.api.InternalLogger
import com.flashcat.rum.sessionreplay.recorder.mapper.TextViewMapper
import com.flashcat.rum.sessionreplay.utils.ColorStringFormatter
import com.flashcat.rum.sessionreplay.utils.DrawableToColorMapper
import com.flashcat.rum.sessionreplay.utils.ViewBoundsResolver
import com.flashcat.rum.sessionreplay.utils.ViewIdentifierResolver

internal open class CheckBoxMapper(
    textWireframeMapper: TextViewMapper<CheckBox>,
    viewIdentifierResolver: ViewIdentifierResolver,
    colorStringFormatter: ColorStringFormatter,
    viewBoundsResolver: ViewBoundsResolver,
    drawableToColorMapper: DrawableToColorMapper,
    internalLogger: InternalLogger
) : CheckableCompoundButtonMapper<CheckBox>(
    textWireframeMapper,
    viewIdentifierResolver,
    colorStringFormatter,
    viewBoundsResolver,
    drawableToColorMapper,
    internalLogger
)
