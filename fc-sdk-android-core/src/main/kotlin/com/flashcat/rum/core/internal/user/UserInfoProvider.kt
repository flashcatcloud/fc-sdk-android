/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.core.internal.user

import com.flashcat.rum.api.context.UserInfo
import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation
internal interface UserInfoProvider {

    fun getUserInfo(): UserInfo
}
