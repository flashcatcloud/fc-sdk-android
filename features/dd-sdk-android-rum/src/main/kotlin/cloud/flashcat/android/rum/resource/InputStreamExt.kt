/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.rum.resource

import cloud.flashcat.android.Flashcat
import cloud.flashcat.android.api.SdkCore
import cloud.flashcat.android.rum.RumMonitor
import java.io.InputStream

/**
 * Allow the [RumMonitor] to track this [InputStream] as a RUM Resource.
 *
 * @param url the url to be associated with this resource
 * @param sdkCore the SDK instance to use. If not provided, default instance will be used.
 */
fun InputStream.asRumResource(url: String, sdkCore: SdkCore = Flashcat.getInstance()): InputStream {
    return RumResourceInputStream(this, url, sdkCore)
}
