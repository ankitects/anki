#!/bin/bash
#
# This makes sure that the sdcard mounts for old emulators, API15-17
# You have to run it once with the non-classic engine after creation though

# We must be in the right directory
~/Android/Sdk/emulator/emulator @$1 -engine classic
