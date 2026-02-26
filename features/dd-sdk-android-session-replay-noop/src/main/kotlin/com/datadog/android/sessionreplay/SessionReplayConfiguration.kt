/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

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
         * Builds a [SessionReplayConfiguration] based on the current state of this Builder.
         */
        fun build(): SessionReplayConfiguration {
            return SessionReplayConfiguration()
        }
    }
}
