/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.android.log

import cloud.flashcat.android.event.EventMapper
import cloud.flashcat.android.event.NoOpEventMapper
import cloud.flashcat.android.log.model.LogEvent

/**
 * Describes configuration to be used for the Logs feature.
 */
data class LogsConfiguration internal constructor(
    internal val customEndpointUrl: String?,
    internal val eventMapper: EventMapper<LogEvent>
) {

    /**
     * A Builder class for a [LogsConfiguration].
     */
    class Builder {
        private var customEndpointUrl: String? = null
        private var logsEventMapper: EventMapper<LogEvent> = NoOpEventMapper()

        /**
         * Let the Logs feature target a custom server.
         * The provided url should be the full endpoint url, e.g.: https://example.com/logs/upload
         */
        fun useCustomEndpoint(endpoint: String): Builder {
            customEndpointUrl = endpoint
            return this
        }

        /**
         * Sets the [EventMapper] for the [LogEvent].
         * You can use this interface implementation to modify the
         * [LogEvent] attributes before serialisation.
         *
         * @param eventMapper the [EventMapper] implementation.
         */
        fun setEventMapper(eventMapper: EventMapper<LogEvent>): Builder {
            logsEventMapper = eventMapper
            return this
        }

        /**
         * Builds a [LogsConfiguration] based on the current state of this Builder.
         */
        fun build(): LogsConfiguration {
            return LogsConfiguration(
                customEndpointUrl = customEndpointUrl,
                eventMapper = logsEventMapper
            )
        }
    }
}
