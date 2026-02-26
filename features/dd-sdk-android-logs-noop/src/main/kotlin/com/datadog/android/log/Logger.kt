/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log

import androidx.annotation.FloatRange
import com.datadog.android.Datadog
import com.datadog.android.api.SdkCore

/**
 * A class enabling Datadog logging features.
 *
 * It allows you to create a specific context (automatic information, custom attributes, tags) that
 * will be embedded in all logs sent through this logger.
 *
 * You can have multiple loggers configured in your application, each with their own settings.
 */
@Suppress("TooManyFunctions", "MethodOverloading", "UNUSED_PARAMETER")
class Logger internal constructor() {

    // region Log

    /**
     * Sends a VERBOSE log message.
     * @param message the message to be logged
     * @param throwable a (nullable) throwable to be logged with the message
     * @param attributes a map of attributes to include only for this message. If an attribute with
     * the same key already exist in this logger, it will be overridden (just for this message)
     */
    @Suppress("FunctionMinLength")
    @JvmOverloads
    fun v(
        message: String,
        throwable: Throwable? = null,
        attributes: Map<String, Any?> = emptyMap()
    ) {}

    /**
     * Sends a Debug log message.
     * @param message the message to be logged
     * @param throwable a (nullable) throwable to be logged with the message
     * @param attributes a map of attributes to include only for this message. If an attribute with
     * the same key already exist in this logger, it will be overridden (just for this message)
     */
    @Suppress("FunctionMinLength")
    @JvmOverloads
    fun d(
        message: String,
        throwable: Throwable? = null,
        attributes: Map<String, Any?> = emptyMap()
    ) {}

    /**
     * Sends an Info log message.
     * @param message the message to be logged
     * @param throwable a (nullable) throwable to be logged with the message
     * @param attributes a map of attributes to include only for this message. If an attribute with
     * the same key already exist in this logger, it will be overridden (just for this message)
     */
    @Suppress("FunctionMinLength")
    @JvmOverloads
    fun i(
        message: String,
        throwable: Throwable? = null,
        attributes: Map<String, Any?> = emptyMap()
    ) {}

    /**
     * Sends a Warning log message.
     * @param message the message to be logged
     * @param throwable a (nullable) throwable to be logged with the message
     * @param attributes a map of attributes to include only for this message. If an attribute with
     * the same key already exist in this logger, it will be overridden (just for this message)
     */
    @Suppress("FunctionMinLength")
    @JvmOverloads
    fun w(
        message: String,
        throwable: Throwable? = null,
        attributes: Map<String, Any?> = emptyMap()
    ) {}

    /**
     * Sends an Error log message.
     * @param message the message to be logged
     * @param throwable a (nullable) throwable to be logged with the message
     * @param attributes a map of attributes to include only for this message. If an attribute with
     * the same key already exist in this logger, it will be overridden (just for this message)
     */
    @Suppress("FunctionMinLength")
    @JvmOverloads
    fun e(
        message: String,
        throwable: Throwable? = null,
        attributes: Map<String, Any?> = emptyMap()
    ) {}

    /**
     * Sends an Assert log message.
     * @param message the message to be logged
     * @param throwable a (nullable) throwable to be logged with the message
     * @param attributes a map of attributes to include only for this message. If an attribute with
     * the same key already exist in this logger, it will be overridden (just for this message)
     */
    @Suppress("FunctionMinLength")
    @JvmOverloads
    fun wtf(
        message: String,
        throwable: Throwable? = null,
        attributes: Map<String, Any?> = emptyMap()
    ) {}

    /**
     * Sends a log message.
     *
     * @param priority the priority level (must be one of the Android Log.* constants)
     * @param message the message to be logged
     * @param throwable a (nullable) throwable to be logged with the message
     * @param attributes a map of attributes to include only for this message. If an attribute with
     * the same key already exist in this logger, it will be overridden (just for this message)
     */
    @JvmOverloads
    fun log(
        priority: Int,
        message: String,
        throwable: Throwable? = null,
        attributes: Map<String, Any?> = emptyMap()
    ) {}

    /**
     * Sends a log message with strings for error information.
     *
     * This method is meant for non-native or cross platform frameworks (such as React Native or
     * Flutter) to send error information to Datadog. Although it can be used directly, it is
     * recommended to use other methods declared on `Logger`.
     *
     * @param priority the priority level (must be one of the Android Log.* constants)
     * @param message the message to be logged
     * @param errorKind the kind of error to be logged with the message
     * @param errorMessage the message from the error to be logged with this message
     * @param errorStacktrace the stack trace from the error to be logged with this message
     * @param attributes a map of attributes to include only for this message. If an attribute with
     * the same key already exist in this logger, it will be overridden (just for this message)
     */
    @JvmOverloads
    @Suppress("LongParameterList")
    fun log(
        priority: Int,
        message: String,
        errorKind: String?,
        errorMessage: String?,
        errorStacktrace: String?,
        attributes: Map<String, Any?> = emptyMap()
    ) {}

    // endregion

    // region Builder

    /**
     * A Builder class for a [Logger].
     *
     * @param sdkCore SDK instance to bind to. If not provided, default instance will be used.
     */
    class Builder
    @JvmOverloads
    constructor(sdkCore: SdkCore = Datadog.getInstance()) {

        /**
         * Builds a [Logger] based on the current state of this Builder.
         */
        fun build(): Logger = Logger()

        /**
         * Sets the service name that will appear in your logs.
         * @param service the service name (default = application package name)
         */
        fun setService(service: String): Builder = this

        /**
         * Sets a minimum threshold (priority) for the log to be sent to the Datadog servers. If log priority
         * is below this one, then it won't be sent. Default value is -1 (allow all).
         * @param minLogThreshold Minimum log threshold to be sent to the Datadog servers.
         */
        fun setRemoteLogThreshold(minLogThreshold: Int): Builder = this

        /**
         * Enables your logs to be duplicated in LogCat.
         * @param enabled false by default
         */
        fun setLogcatLogsEnabled(enabled: Boolean): Builder = this

        /**
         * Enables network information to be automatically added in your logs.
         * @param enabled false by default
         */
        fun setNetworkInfoEnabled(enabled: Boolean): Builder = this

        /**
         * Sets the logger name that will appear in your logs when a throwable is attached.
         * @param name the logger custom name (default = application package name)
         */
        fun setName(name: String): Builder = this

        /**
         * Enables the logs bundling with the current active trace. If this feature is enabled all
         * the logs from this moment on will be bundled with the current trace and you will be able
         * to see all the logs sent during a specific trace.
         * @param enabled true by default
         */
        fun setBundleWithTraceEnabled(enabled: Boolean): Builder = this

        /**
         * Enables the logs bundling with the current active View. If this feature is enabled all
         * the logs from this moment on will be bundled with the current view information and you
         * will be able to see all the logs sent during a specific view in the Rum Explorer.
         * @param enabled true by default
         */
        fun setBundleWithRumEnabled(enabled: Boolean): Builder = this

        /**
         * Sets the sample rate for this Logger.
         * @param sampleRate the sample rate, in percent.
         * A value of `30` means we'll send 30% of the logs. If value is `0`, no logs will be sent
         * to Datadog.
         * Default is 100.0 (ie: all logs are sent).
         */
        fun setRemoteSampleRate(@FloatRange(from = 0.0, to = 100.0) sampleRate: Float): Builder = this
    }

    // endregion

    // region Context Information (attributes, tags)
    /**
     * Add a custom attribute to all future logs sent by this logger.
     *
     * Values can be nested up to 10 levels deep. Keys
     * using more than 10 levels will be sanitized by SDK.
     *
     * @param key the key for this attribute
     * @param value the attribute value
     */
    fun addAttribute(key: String, value: Any?) {}

    /**
     * Remove a custom attribute from all future logs sent by this logger.
     * Previous logs won't lose the attribute value associated with this key if they were created
     * prior to this call.
     * @param key the key of the attribute to remove
     */
    fun removeAttribute(key: String) {}

    /**
     * Add a tag to all future logs sent by this logger.
     * The tag will take the form "key:value".
     *
     * Tags must start with a letter and after that may contain the following characters:
     * Alphanumerics, Underscores, Minuses, Colons, Periods, Slashes. Other special characters
     * are converted to underscores.
     * Tags must be lowercase, and can be at most 200 characters. If the tag you provide is
     * longer, only the first 200 characters will be used.
     *
     * @param key the key for this tag
     * @param value the (non null) value of this tag
     * @see [documentation](https://docs.datadoghq.com/tagging/#defining-tags)
     */
    fun addTag(key: String, value: String) {}

    /**
     * Add a tag to all future logs sent by this logger.
     *
     * Tags must start with a letter and after that may contain the following characters:
     * Alphanumerics, Underscores, Minuses, Colons, Periods, Slashes. Other special characters
     * are converted to underscores.
     * Tags must be lowercase, and can be at most 200 characters. If the tag you provide is
     * longer, only the first 200 characters will be used.
     *
     * @param tag the (non null) tag
     * @see [documentation](https://docs.datadoghq.com/tagging/#defining-tags)
     */
    fun addTag(tag: String) {}

    /**
     * Remove a tag from all future logs sent by this logger.
     * Previous logs won't lose the this tag if they were created prior to this call.
     * @param tag the tag to remove
     */
    fun removeTag(tag: String) {}

    /**
     * Remove all tags with the given key from all future logs sent by this logger.
     * Previous logs won't lose the this tag if they were created prior to this call.
     * @param key the key of the tags to remove
     */
    fun removeTagsWithKey(key: String) {}

    // endregion
}
