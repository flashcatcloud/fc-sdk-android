/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

#include <pthread.h>
#include <stdexcept>
#include <string>
#include "greatest/greatest.h"
#include "utils/string-utils.h"

TEST test_generates_formatted_string(void) {
    const char* formatter = "Given %s will return %s from %s";
    const std::string formatted_string = stringutils::format(formatter, "A", "B", "C");
    const std::string expected_formatted_string = "Given A will return B from C";
            ASSERT_STR_EQ(formatted_string.c_str(), expected_formatted_string.c_str());
            PASS();
}

TEST test_formats_large_string_on_small_thread_stack(void) {
    struct FormatResult {
        std::string input;
        std::string output;
    } result{std::string(128 * 1024, 'a'), {}};

    pthread_attr_t attributes;
    ASSERT_EQ(0, pthread_attr_init(&attributes));
    const int stack_size_status = pthread_attr_setstacksize(&attributes, 64 * 1024);
    if (stack_size_status != 0) {
        pthread_attr_destroy(&attributes);
    }
    ASSERT_EQ(0, stack_size_status);

    pthread_t thread;
    const int create_status = pthread_create(&thread, &attributes, [](void* argument) -> void* {
        auto& result = *static_cast<FormatResult*>(argument);
        result.output = stringutils::format("%s", result.input.c_str());
        return nullptr;
    }, &result);
    pthread_attr_destroy(&attributes);
    ASSERT_EQ(0, create_status);
    ASSERT_EQ(0, pthread_join(thread, nullptr));
    ASSERT(result.input == result.output);
    PASS();
}

TEST test_does_not_throw_exception_if_not_enough_arguments(void) {
    std::runtime_error *expected_error = nullptr;
    try {
        const std::string formatted_string = stringutils::format("%s %s %s %s", "A", "B", "C");
    }
    catch (std::runtime_error &error) {
        expected_error = &error;
    }
            ASSERT(expected_error == nullptr);
            PASS();
}

TEST test_does_not_throw_exception_if_not_enough_arguments_placeholders(void) {
    std::runtime_error *expected_error = nullptr;
    try {
        const std::string formatted_string = stringutils::format("%s", "A", "B", "C");
    }
    catch (std::runtime_error &error) {
        expected_error = &error;
    }
            ASSERT(expected_error == nullptr);
            PASS();
}

SUITE (string_utils) {
            RUN_TEST(test_generates_formatted_string);
            RUN_TEST(test_formats_large_string_on_small_thread_stack);
            // TODO RUMM-0000 disable this one, because snprintf gives SIGSEGV on ARM64 (Apple Silicon)
            // strange enough the test just below is ok
            // RUN_TEST(test_does_not_throw_exception_if_not_enough_arguments);
            RUN_TEST(test_does_not_throw_exception_if_not_enough_arguments_placeholders);
}

