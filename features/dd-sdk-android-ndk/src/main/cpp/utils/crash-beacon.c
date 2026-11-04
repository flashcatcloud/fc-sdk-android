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

static volatile sig_atomic_t armed = 0;
static volatile sig_atomic_t timeout_ms = 1000;
/* The signal the beacon was woken for; 0 until a crash claims the beacon. Accessed atomically. */
static int crash_signal = 0;
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
    crash_beacon_installed = 1;
    return true;
}

void crash_beacon_uninstall(void) {
    if (!crash_beacon_installed) {
        return;
    }
    crash_beacon_installed = 0;
    armed = 0;
    // No crash is claimed, so the beacon thread wakes up to nothing and ends.
    write_byte(wake_fds[1]);
}

void crash_beacon_arm(bool should_arm, int wait_ms) {
    timeout_ms = wait_ms;
    armed = should_arm ? 1 : 0;
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

bool crash_beacon_is_sending(void) {
    return __atomic_load_n(&crash_signal, __ATOMIC_SEQ_CST) != 0;
}

static long elapsed_ms(const struct timespec *since) {
    struct timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now);
    return (now.tv_sec - since->tv_sec) * 1000L + (now.tv_nsec - since->tv_nsec) / 1000000L;
}

void crash_beacon_on_crash(int signum) {
    if (!crash_beacon_installed || !armed) {
        return;
    }
    // One beacon per process: the first crash to get here claims it.
    if (__atomic_exchange_n(&crash_signal, signum, __ATOMIC_SEQ_CST) != 0) {
        return;
    }
    if (!write_byte(wake_fds[1])) {
        // Nothing woke up, so nothing is under way: later crashes must not wait on it.
        __atomic_store_n(&crash_signal, 0, __ATOMIC_SEQ_CST);
        return;
    }

    struct timespec start;
    clock_gettime(CLOCK_MONOTONIC, &start);
    const long wait_ms = timeout_ms;
    for (;;) {
        const long remaining = wait_ms - elapsed_ms(&start);
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
