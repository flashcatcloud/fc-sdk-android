/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

#include <atomic>
#include <thread>

#include "greatest/greatest.h"
#include "utils/crash-beacon.h"

TEST only_one_concurrent_crash_can_claim_the_report(void) {
    ASSERT(crash_beacon_install());
    crash_beacon_arm(true, 1000);
    std::atomic<int> ready{0};
    std::atomic<int> winners{0};
    auto claim = [&] {
        ready.fetch_add(1);
        while (ready.load() != 2) std::this_thread::yield();
        if (crash_beacon_try_claim(SIGSEGV)) winners.fetch_add(1);
    };
    std::thread first(claim);
    std::thread second(claim);
    first.join();
    second.join();
    ASSERT_EQ(1, winners.load());
    crash_beacon_reset();
    crash_beacon_uninstall();
    // Consume the shutdown wake, as the Java worker does.
    ASSERT_EQ(0, crash_beacon_wait_for_crash());
    PASS();
}

TEST a_losing_crash_cannot_replace_the_signal_or_budget(void) {
    ASSERT(crash_beacon_install());
    crash_beacon_arm(true, 500);
    ASSERT(crash_beacon_try_claim(SIGSEGV));
    ASSERT_FALSE(crash_beacon_try_claim(SIGABRT));
    ASSERT(crash_beacon_is_owner());
    // Updating the remote timeout must not extend or truncate this crash's wait.
    crash_beacon_arm(true, 2000);
    crash_beacon_record_report(5000, 4000);
    int signal = 0;
    int remaining = 0;
    int64_t timestamp = 0;
    int64_t uptime = 0;
    std::thread worker([&] {
        signal = crash_beacon_wait_for_crash();
        remaining = crash_beacon_remaining_wait_ms();
        timestamp = crash_beacon_report_time();
        uptime = crash_beacon_report_uptime();
        crash_beacon_finish();
    });
    crash_beacon_on_crash();
    worker.join();
    ASSERT_EQ(SIGSEGV, signal);
    ASSERT(remaining <= 500);
    ASSERT_EQ(5000, timestamp);
    ASSERT_EQ(4000, uptime);
    crash_beacon_reset();
    crash_beacon_uninstall();
    ASSERT_EQ(0, crash_beacon_wait_for_crash());
    PASS();
}

SUITE(crash_beacon) {
    RUN_TEST(only_one_concurrent_crash_can_claim_the_report);
    RUN_TEST(a_losing_crash_cannot_replace_the_signal_or_budget);
}
