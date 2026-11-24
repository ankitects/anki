#!/bin/bash
# SPDX-License-Identifier: GPL-3.0-or-later

# This sets the "Don't keep activities" developer option and reboots the emulator to enable it.
# This script does not wait for the reboot before exiting.
#
# Usage: dont_keep_activities.sh 1|0
#
# This expects one connected device, set ANDROID_SERIAL if multiple devices are attached.
# https://developer.android.com/tools/variables

if [ "$1" != "1" ] && [ "$1" != "0" ]; then
  echo "You must provide '1' or '0' as the argument"
  exit 1
fi

adb shell settings put global always_finish_activities $1

# writing is async; wait for it (took < 3s)
sleep 6

# a reboot is necessary for the setting to be persisted if not set via the Developer settings UI.
adb reboot
