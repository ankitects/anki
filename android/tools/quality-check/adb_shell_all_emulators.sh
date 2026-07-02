#!/bin/bash

# This attempts to send an adb shell command to all your emulators (on Ubuntu 18.04.01 LTS at least...) 

if [ "$1" == "" ]; then
  echo "You must provide the adb shell command to execute as the argument"
  exit 1
fi

killall -9 adb
adb devices -l > /dev/null
sleep 2
adb devices -l > /dev/null
sleep 2

for EMU_ID in `adb devices -l | grep emulator | cut -d' ' -f1`; do
  echo "adb -s $EMU_ID shell $1 $2 $3 $4 $5 $6"
  adb -s $EMU_ID shell $1 $2 $3 $4 $5 $6
done
