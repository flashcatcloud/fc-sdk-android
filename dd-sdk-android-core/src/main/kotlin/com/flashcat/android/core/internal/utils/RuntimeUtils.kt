/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.  * Modified 2025 by FlashCat, Inc.
 */

package com.flashcat.android.core.internal.utils

import com.flashcat.android.api.InternalLogger

// Use it only when there is no way to access the SDK-specific logger.
internal var unboundInternalLogger: InternalLogger = InternalLogger.UNBOUND
