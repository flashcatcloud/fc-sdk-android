/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import androidx.annotation.FloatRange
import com.datadog.android.api.InternalLogger

/**
 * Describes configuration to be used for the Session Replay feature.
 */
@Suppress("UNUSED_PARAMETER")
class SessionReplayConfiguration internal constructor() {

    /**
     * A Builder class for a [SessionReplayConfiguration].
     */
    class Builder {
        /**
         * Calling this constructor will default to a 100% session sampling rate.
         */
        constructor() : this(100.0f, InternalLogger.UNBOUND)

        /**
         * @param sampleRate must be a value between 0 and 100. A value of 0
         * means no session will be recorded, 100 means all sessions will be recorded.
         * If this value is not provided then Session Replay will default to a 100 sample rate.
         */
        constructor(
            @FloatRange(from = 0.0, to = 100.0) sampleRate: Float = 100.0f
        ) : this(sampleRate, InternalLogger.UNBOUND)

        internal constructor(
            @FloatRange(from = 0.0, to = 100.0) sampleRate: Float,
            logger: InternalLogger
        ) {
        }

        /**
         * Sets the sample rate for this feature.
         * @param sampleRate the sample rate, in percent. A value of `30` means we'll send 30%
         * of the sessions. If value is `0`, no session will be recorded.
         * Default is 0.0.
         */
        fun setSampleRate(sampleRate: Float): Builder {
            return this
        }

        /**
         * Let the Session Replay feature target a custom server.
         * @param endpoint the full endpoint url, e.g.: https://example.com/session-replay/upload
         */
        fun useCustomEndpoint(endpoint: String): Builder {
            return this
        }

        /**
         * Sets the privacy level for this feature.
         * @param privacyLevel the privacy level to use.
         * Default is [SessionReplayPrivacy.MASK].
         * @see [SessionReplayPrivacy]
         */
        fun setPrivacy(privacyLevel: SessionReplayPrivacy): Builder {
            return this
        }

        /**
         * Adds an extension support for the Session Replay feature.
         * @param extensionSupport the extension support to add.
         */
        fun addExtensionSupport(extensionSupport: ExtensionSupport): Builder {
            return this
        }

        /**
         * Sets the dynamic privacy rules for this feature.
         * @param imagePrivacy the image privacy to use.
         */
        fun setImagePrivacy(imagePrivacy: ImagePrivacy): Builder {
            return this
        }

        /**
         * Sets the touch privacy for this feature.
         * @param touchPrivacy the touch privacy to use.
         */
        fun setTouchPrivacy(touchPrivacy: TouchPrivacy): Builder {
            return this
        }

        /**
         * Sets the dynamic privacy rules for this feature.
         * @param textAndInputPrivacy the text and input privacy to use.
         */
        fun setTextAndInputPrivacy(textAndInputPrivacy: TextAndInputPrivacy): Builder {
            return this
        }

        /**
         * Sets the system requirements configuration for the Session Replay feature.
         * @param systemRequirementsConfiguration the system requirements configuration to use.
         */
        fun setSystemRequirementsConfiguration(
            systemRequirementsConfiguration: SystemRequirementsConfiguration
        ): Builder {
            return this
        }

        /**
         * This option controls whether optimization is enabled or disabled for recording Session Replay data.
         * By default the value is true, meaning the dynamic optimization is enabled.
         */
        fun setDynamicOptimizationEnabled(dynamicOptimizationEnabled: Boolean): Builder {
            return this
        }

        /**
         * Defines the minimum system requirements for enabling the Session Replay feature.
         * When [SessionReplay.enable] is invoked, the system configuration is verified against these requirements.
         * If the system meets the specified criteria, Session Replay will be successfully enabled.
         * If this function is not invoked, no minimum requirements will be enforced, and Session Replay will be
         * enabled on all devices.
         */
        fun setSystemRequirements(systemRequirementsConfiguration: SystemRequirementsConfiguration): Builder {
            return this
        }

        /**
         * Should recording start automatically (or be manually started).
         * If not specified then by default it starts automatically.
         * @param enabled whether recording should start automatically or not.
         */
        fun startRecordingImmediately(enabled: Boolean): Builder {
            return this
        }

        /**
         * Builds a [SessionReplayConfiguration] based on the current state of this Builder.
         */
        fun build(): SessionReplayConfiguration {
            return SessionReplayConfiguration()
        }
    }
}
