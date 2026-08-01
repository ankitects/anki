#!/bin/bash

# This will start a fleet of android emulators that have been created using avdmanager or Android Studio
#
# You should follow the convention of naming them xxx_OLD for API15-17, and xxx_NEW for API18+
# Name ARM emulators (if you have them) xxxARM and make sure Chomebook is in the Chromebook emulators name
# as adb isn't available until you log in on Chromebooks
#
# Final note is that for the OLD emulators, you create them with avdmanager or Android Studio, then
# you have to start them once the *new* way to initialize things, but the sdcard won't mount. Then you
# start them the old way and everything works. If you get it wrong, either you won't have an sdcard, or
# the emulator will fail to boot with "Failed to decrypt" or similar
#
# Note that if you have many emulators, you may need to increase your file handles or you'll run out
# of file handles in your user session and enjoy very strange behavior (Chrome extensions crashing, 
# terminals behaving strangely etc)

SLEEP=$1
if [ "$SLEEP" == "" ];
  then SLEEP=10
else
  SLEEP=0
fi


for AVD in `emulator -list-avds`; do
  echo -n Found $AVD...

  #SDCARD="/tmp/$AVD-sdcard.img"
  NORMAL_ARGS="-no-snapshot -no-boot-anim " #-sdcard $SDCARD"
  EXTRA_ARGS=""

  case "$AVD" in
    #*21*)
    #  echo "API 21 is problematic, skipping for now..."
    #  continue
    #  ;;
    #*15*)
    #  echo "API 15 is problematic, skipping for now..."
    #  continue
    #  ;;
    *OLD*)
      # Name your emulators with an "OLD" tag for API <=17 or sdcard doesn't auto-mount
      echo "$AVD is old, using workaround..."
      EXTRA_ARGS="$EXTRA_ARGS -engine classic"
      ;;
    *NEW*)
      # Name your emulators with a "NEW" tag for API >17 
      echo "$AVD is new, normal emulator..."
      ;;
    *ARM*)
      # Don't use ARM emulators by default on x86 (so slow...)
      echo "Skipping ARM emulator $AVD..."
      continue
      ;;
    *Chromebook*)
      # Don't use Chromebook emulators by default
      echo "Skipping Chromebook emulator $AVD..."
      continue
      ;;
  esac

  #$ANDROID_SDK/tools/mksdcard -l sdcard 100M $SDCARD
  $ANDROID_SDK/emulator/emulator $NORMAL_ARGS $EXTRA_ARGS @$AVD &
  sleep $SLEEP
done
