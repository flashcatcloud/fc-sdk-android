/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.core.internal.utils

import com.flashcat.rum.api.InternalLogger

// Use it only when there is no way to access the SDK-specific logger.
internal var unboundInternalLogger: InternalLogger = InternalLogger.UNBOUND
