#!/bin/bash
# SPDX-License-Identifier: GPL-3.0-or-later

set -euo pipefail

# Only for dedicated Linux CI runners with one emulator and -no-snapshot-save.
# Forced shutdown must not interrupt saving a snapshot for subsequent jobs.
stop_emulator() {
  # #22613 `emu kill` can report success while QEMU keeps running and
  # holds the emulator action's output pipes open. Bound both ADB and QEMU.
  timeout -s KILL 5s "$ANDROID_HOME/platform-tools/adb" -s "emulator-$EMULATOR_PORT" emu kill || true

  local emulator_pattern="^$ANDROID_HOME/emulator/qemu/"
  local deadline=$((SECONDS + 20))
  while pgrep -f "$emulator_pattern" > /dev/null; do
    if ((SECONDS >= deadline)); then
      echo "Emulator did not stop within 20 seconds; killing QEMU." >&2
      pkill -KILL -f "$emulator_pattern" || true
      break
    fi
    sleep 1
  done
}

# Preserve the exit status
trap stop_emulator EXIT
"$@"
