/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

// FLASHCAT FORK - the crash beacon of native crashes.
//
// The crashed thread never sends anything. Once the crash report is written, it writes one byte to
// a pipe a beacon thread (prepared at startup, on the Java side) is blocked reading, and waits for
// its answer on a second pipe for at most the beacon timeout. write(), poll() and clock_gettime()
// are async-signal-safe; nothing else runs on the crashed thread.
//
// Off unless the app turned the beacon on: until crash_beacon_install() succeeds, the signal
// handler only reads crash_beacon_installed, which is 0, and the crash goes exactly as before.

#ifndef CRASH_BEACON_H
#define CRASH_BEACON_H

#include <signal.h>
#include <stdbool.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* 1 once the beacon pipes exist; the only state the signal handler reads when the beacon is off. */
extern volatile sig_atomic_t crash_beacon_installed;

/* Creates the pipes, once. Returns false, and leaves the beacon off, when they cannot be. */
bool crash_beacon_install(void);

/* Turns the beacon off and ends the beacon thread's wait. The pipes stay open, see crash-beacon.c. */
void crash_beacon_uninstall(void);

/* Whether a crash now wakes the beacon thread, and how long the crashed thread waits for it. */
void crash_beacon_arm(bool armed, int timeout_ms);

/* Beacon thread: blocks until a crash; returns its signal, or 0 once the beacon is uninstalled. */
int crash_beacon_wait_for_crash(void);

/* Beacon thread: tells the crashed thread it may go on. */
void crash_beacon_finish(void);

/* Claim before unwinding or writing. A losing thread must not touch the report or handler chain. */
bool crash_beacon_try_claim(int signum);
/* A recursive fault while unwinding must never park the owner itself. */
bool crash_beacon_is_owner(void);

/* The exact time pair of the successfully written native report, published before waking Java. */
void crash_beacon_record_report(int64_t timestamp_ms, int64_t uptime_ms);
int64_t crash_beacon_report_time(void);
int64_t crash_beacon_report_uptime(void);
int crash_beacon_remaining_wait_ms(void);

/* Signal handler: wakes the beacon thread and waits for it, within the timeout. */
void crash_beacon_on_crash(void);

/* Signal handler: the crash was survived (the previous handler returned); a later one starts afresh. */
void crash_beacon_reset(void);

/*
 * Signal handler, for a crash while a beacon is under way. Parks this thread for good, so the first
 * crash's report is kept and the first crash ends the process; a crash on the beacon thread itself
 * also lets the first crash go on at once instead of waiting out the timeout.
 */
void crash_beacon_park(void);

#ifdef __cplusplus
}
#endif

#endif // CRASH_BEACON_H
