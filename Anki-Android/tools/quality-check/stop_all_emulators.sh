#!/bin/bash

# This attempts to stop all your emulators (on Ubuntu 18.10 at least...) nicely
#
# ...then if they don't stop it kills them

for EMU_ID in `adb devices -l | grep emulator | cut -d' ' -f1`; do
  echo Stopping emulator $EMU_ID...
  adb -s $EMU_ID emu kill
done

sleep 10
for PID in `ps -eo pid,cmd,args |grep emulator|grep Android|grep -v bash|grep -v crash|grep -v grep|cut -d/ -f1`; do
  echo "Stopping emulator with $PID..."
  kill $PID
done
