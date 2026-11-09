/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

// pipe2()
#define _GNU_SOURCE

#include "crash-beacon.h"

#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <time.h>
#include <unistd.h>

volatile sig_atomic_t crash_beacon_installed = 0;

/* Publish enabled and timeout together; one crash freezes one configuration. */
static int arm_state = 1000 << 1;
static int claimed_arm_state = 0;
static int64_t report_time_ms = 0;
static int64_t report_uptime_ms = 0;
static struct timespec wait_started;
/* The signal the beacon was woken for; 0 until a crash claims the beacon. Accessed atomically. */
static int crash_signal = 0;
static int owner_tid = 0;
/* The beacon thread, once it waits: a crash on it must let the crashed thread it serves go on. */
static volatile pid_t beacon_tid = 0;

/*
 * Created once and never closed: a crash may be using them at any moment, and a closed descriptor
 * can be reused by anything. Uninstalling wakes the beacon thread instead, with no crash claimed.
 */
static int wake_fds[2] = {-1, -1};
static int done_fds[2] = {-1, -1};

static bool create_pipes(void) {
    if (wake_fds[0] >= 0) {
        return true;
    }
    int wake[2];
    int done[2];
    if (pipe2(wake, O_CLOEXEC) != 0) {
        return false;
    }
    if (pipe2(done, O_CLOEXEC) != 0) {
        close(wake[0]);
        close(wake[1]);
        return false;
    }
    done_fds[0] = done[0];
    done_fds[1] = done[1];
    wake_fds[0] = wake[0];
    wake_fds[1] = wake[1];
    return true;
}

static bool write_byte(int fd) {
    const char byte = 1;
    ssize_t n;
    do {
        n = write(fd, &byte, 1);
    } while (n < 0 && errno == EINTR);
    return n == 1;
}

bool crash_beacon_install(void) {
    if (crash_beacon_installed) {
        return true;
    }
    if (!create_pipes()) {
        return false;
    }
    __atomic_store_n(&crash_signal, 0, __ATOMIC_SEQ_CST);
    __atomic_store_n(&owner_tid, 0, __ATOMIC_SEQ_CST);
    crash_beacon_installed = 1;
    return true;
}

void crash_beacon_uninstall(void) {
    if (!crash_beacon_installed) {
        return;
    }
    crash_beacon_installed = 0;
    __atomic_store_n(&arm_state, 1000 << 1, __ATOMIC_SEQ_CST);
    // No crash is claimed, so the beacon thread wakes up to nothing and ends.
    write_byte(wake_fds[1]);
}

void crash_beacon_arm(bool should_arm, int wait_ms) {
    if (wait_ms < 500 || wait_ms > 2000) wait_ms = 1000;
    __atomic_store_n(&arm_state, (wait_ms << 1) | (should_arm ? 1 : 0), __ATOMIC_SEQ_CST);
}

int crash_beacon_wait_for_crash(void) {
    beacon_tid = gettid();
    char byte;
    ssize_t n;
    do {
        n = read(wake_fds[0], &byte, 1);
    } while (n < 0 && errno == EINTR);
    return n == 1 ? __atomic_load_n(&crash_signal, __ATOMIC_SEQ_CST) : 0;
}

void crash_beacon_finish(void) {
    write_byte(done_fds[1]);
}

bool crash_beacon_try_claim(int signum) {
    int expected = 0;
    if (!__atomic_compare_exchange_n(&owner_tid, &expected, gettid(), false,
                                    __ATOMIC_SEQ_CST, __ATOMIC_SEQ_CST)) return false;
    __atomic_store_n(&crash_signal, signum, __ATOMIC_SEQ_CST);
    claimed_arm_state = __atomic_load_n(&arm_state, __ATOMIC_SEQ_CST);
    report_time_ms = 0;
    report_uptime_ms = 0;
    return true;
}

bool crash_beacon_is_owner(void) {
    return __atomic_load_n(&owner_tid, __ATOMIC_SEQ_CST) == gettid();
}

void crash_beacon_record_report(int64_t timestamp_ms, int64_t uptime_ms) {
    report_time_ms = timestamp_ms;
    report_uptime_ms = uptime_ms;
}

int64_t crash_beacon_report_time(void) { return report_time_ms; }
int64_t crash_beacon_report_uptime(void) { return report_uptime_ms; }

static long elapsed_ms(const struct timespec *since) {
    struct timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now);
    return (now.tv_sec - since->tv_sec) * 1000L + (now.tv_nsec - since->tv_nsec) / 1000000L;
}

/* Drops an answer left over from a crash the process survived, so it cannot end this wait early. */
static void drain_done_pipe(void) {
    char byte;
    struct pollfd done = {.fd = done_fds[0], .events = POLLIN, .revents = 0};
    while (poll(&done, 1, 0) > 0 && (done.revents & POLLIN)) {
        if (read(done_fds[0], &byte, 1) != 1) {
            return;
        }
    }
}

int crash_beacon_remaining_wait_ms(void) {
    const long remaining = (claimed_arm_state >> 1) - elapsed_ms(&wait_started);
    return remaining > 0 ? (int) remaining : 0;
}

void crash_beacon_on_crash(void) {
    // Only the owner reaches here, and only a successfully written report may send a beacon.
    if (!(claimed_arm_state & 1) || report_time_ms == 0) return;
    drain_done_pipe();
    clock_gettime(CLOCK_MONOTONIC, &wait_started);
    if (!write_byte(wake_fds[1])) {
        // Nothing woke up, so nothing is under way: later crashes must not wait on it.
        __atomic_store_n(&crash_signal, 0, __ATOMIC_SEQ_CST);
        return;
    }

    for (;;) {
        const long remaining = crash_beacon_remaining_wait_ms();
        if (remaining <= 0) {
            return;
        }
        struct pollfd done = {.fd = done_fds[0], .events = POLLIN, .revents = 0};
        const int ready = poll(&done, 1, (int) remaining);
        if (ready != 0 && !(ready < 0 && errno == EINTR)) {
            // answered, or the pipe is unusable: either way, stop waiting
            return;
        }
    }
}

void crash_beacon_reset(void) {
    __atomic_store_n(&crash_signal, 0, __ATOMIC_SEQ_CST);
    __atomic_store_n(&owner_tid, 0, __ATOMIC_SEQ_CST);
}

void crash_beacon_park(void) {
    // Only the beacon thread releases the crashed thread: it was the one it waited for. Any other
    // thread just stays out of the way while the first crash ends the process.
    if (gettid() == beacon_tid) {
        crash_beacon_finish();
    }
    for (;;) {
        pause();
    }
}
