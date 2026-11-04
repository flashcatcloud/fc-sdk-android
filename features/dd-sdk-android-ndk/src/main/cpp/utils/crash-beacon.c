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
/* The signal the beacon was woken for; 0 until a crash. */
static volatile sig_atomic_t crash_signal = 0;

static int wake_fds[2] = {-1, -1};
static int done_fds[2] = {-1, -1};

static void close_pipe(int fds[2]) {
    if (fds[0] >= 0) close(fds[0]);
    if (fds[1] >= 0) close(fds[1]);
    fds[0] = -1;
    fds[1] = -1;
}

bool crash_beacon_install(void) {
    if (crash_beacon_installed) {
        return true;
    }
    if (pipe2(wake_fds, O_CLOEXEC) != 0) {
        return false;
    }
    if (pipe2(done_fds, O_CLOEXEC) != 0) {
        close_pipe(wake_fds);
        return false;
    }
    crash_signal = 0;
    crash_beacon_installed = 1;
    return true;
}

void crash_beacon_uninstall(void) {
    if (!crash_beacon_installed) {
        return;
    }
    crash_beacon_installed = 0;
    armed = 0;
    close_pipe(wake_fds);
    close_pipe(done_fds);
}

void crash_beacon_arm(bool should_arm, int wait_ms) {
    timeout_ms = wait_ms;
    armed = should_arm ? 1 : 0;
}

int crash_beacon_wait_for_crash(void) {
    char byte;
    ssize_t n;
    do {
        n = read(wake_fds[0], &byte, 1);
    } while (n < 0 && errno == EINTR);
    return n == 1 ? (int) crash_signal : 0;
}

void crash_beacon_finish(void) {
    const char byte = 1;
    ssize_t n;
    do {
        n = write(done_fds[1], &byte, 1);
    } while (n < 0 && errno == EINTR);
}

bool crash_beacon_is_sending(void) {
    return crash_signal != 0;
}

static long elapsed_ms(const struct timespec *since) {
    struct timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now);
    return (now.tv_sec - since->tv_sec) * 1000L + (now.tv_nsec - since->tv_nsec) / 1000000L;
}

void crash_beacon_on_crash(int signum) {
    if (!crash_beacon_installed || !armed || crash_signal != 0) {
        return;
    }
    crash_signal = signum;

    const char byte = 1;
    if (write(wake_fds[1], &byte, 1) != 1) {
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

void crash_beacon_park(void) {
    crash_beacon_finish();
    for (;;) {
        pause();
    }
}
