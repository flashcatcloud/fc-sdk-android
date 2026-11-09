/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */


#include <jni.h>
#include <cstdio>
#include <string>


#ifndef STRINGUTILS_H
#define STRINGUTILS_H


namespace stringutils {
    std::string copy_to_string(JNIEnv *env, jstring from);

    template<typename... Args>
    std::string format(const char* formatter, Args... args) {
        const int length = std::snprintf(nullptr, 0, formatter, args...);
        if (length <= 0) {
            return {};
        }
        const auto size = static_cast<size_t>(length);
        // Crash reporting can run on a small alternate signal stack.
        std::string buffer(size + 1, '\0');
        std::snprintf(buffer.data(), buffer.size(), formatter, args...);
        buffer.resize(size);
        return buffer;
    }
}

#endif

