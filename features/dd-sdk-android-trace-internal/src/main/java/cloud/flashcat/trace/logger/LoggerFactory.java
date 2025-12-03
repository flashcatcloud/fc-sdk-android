/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.trace.logger;

import androidx.annotation.NonNull;

import cloud.flashcat.android.api.InternalLogger;

public final class LoggerFactory {

    @NonNull
    public static Logger getLogger(String name) {
        return new NoOpLogger();
    }
    @NonNull
    public static Logger getLogger(String name, InternalLogger internalLogger) {
        return new DatadogCoreTracerLogger(name, internalLogger);
    }

    @NonNull
    public static Logger getLogger(Class<?> clazz) {
        return new NoOpLogger();
    }

    public static ILoggerFactory getILoggerFactory() {
        return new ILoggerFactory() {
            @Override
            public Logger getLogger(String name) {
                return new NoOpLogger();
            }

            @Override
            public Logger getLogger(String name, InternalLogger internalLogger) {
                return new DatadogCoreTracerLogger(name, internalLogger);
            }
        };
    }
}
