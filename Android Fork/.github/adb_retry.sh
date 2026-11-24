#!/bin/bash
# SPDX-License-Identifier: GPL-3.0-or-later

set -euo pipefail

# ADB can report the device offline just after boot (#22033/#22042).
# Retry only connection failures reported before the command is dispatched.
# Only use for short-lived commands: stderr is captured until they exit.
stderr_file=$(mktemp)
trap 'rm -f "$stderr_file"' EXIT

for ((attempt = 1; attempt <= 5; attempt++)); do
  # Keep retry messages out of stdout so getprop results remain usable.
  if adb "$@" 2>"$stderr_file"; then
    cat "$stderr_file" >&2
    exit 0
  else
    adb_status=$?
  fi
  cat "$stderr_file" >&2

  if ((attempt == 5)); then
    exit "$adb_status"
  fi
  case "$(<"$stderr_file")" in
    "adb: device offline"|"error: device offline") ;;
    *) exit "$adb_status" ;;
  esac
  printf 'ADB device offline; retrying in 1 second (attempt %s/5).\n' "$((attempt + 1))" >&2
  sleep 1
done
