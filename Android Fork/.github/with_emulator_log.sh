#!/bin/bash
# SPDX-License-Identifier: GPL-3.0-or-later

set -euo pipefail

log_file=$1
shift

# Fail before starting the test command if we cannot capture its logs.
: >> "$log_file"

stop_process() {
  kill "$1" 2>/dev/null || true
  wait "$1" 2>/dev/null || true
}

capture_logcat() {
  local child_pid=
  trap 'exit 0' INT TERM
  trap 'stop_process "$child_pid"' EXIT

  # ADB can disconnect after boot, ending logcat before the tests start (Issue 22115).
  # Reconnect even on a clean exit. Replaying the buffer also recovers events emitted
  # while disconnected; the test reporter deduplicates ignored/failed events.
  while true; do
    # Background both commands so TERM interrupts wait, including during the delay.
    adb logcat '*:D' >> "$log_file" &
    child_pid=$!
    wait "$child_pid" || true
    echo 'Emulator logcat exited; reconnecting in 1 second.' >&2
    sleep 1 &
    child_pid=$!
    wait "$child_pid" || true
  done
}

capture_logcat &
capture_pid=$!
# Stop both the retry loop and its child before returning the test command's status.
trap 'stop_process "$capture_pid"' EXIT
"$@"
