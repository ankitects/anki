#!/bin/bash
# enable/disable scoped storage: ./set_scoped_storage.sh [limited/full]


function help() {
    echo -e "\nPossible arguments:"
    echo -e "limited\t\tEquivalent to a fresh install. /AnkiDroid/ is inaccessible"
    echo -e "full\t\tEquivalent to an upgrade from targetSdkVersion 29 to 30. /AnkiDroid/ is accessible\n"
}

# This should be run when a single AOSP emulator is open.
# Errors are suppressed by default. Remove > /dev/null
if [ "$1" = "full" ]; then
    adb shell pm grant com.ichi2.anki.debug android.permission.READ_EXTERNAL_STORAGE
    adb shell pm grant com.ichi2.anki.debug android.permission.WRITE_EXTERNAL_STORAGE
    adb shell am compat disable FORCE_ENABLE_SCOPED_STORAGE com.ichi2.anki.debug   > /dev/null
    adb shell am compat disable DEFAULT_SCOPED_STORAGE com.ichi2.anki.debug  > /dev/null # fails on Play store
    if [ $? == '0' ]; then
        echo "scoped storage disabled: preserving storage access"
    else
        echo "something went wrong: edit script to display errors"
    fi
elif [ "$1" = "limited" ]; then
    adb shell pm revoke com.ichi2.anki.debug android.permission.READ_EXTERNAL_STORAGE
    adb shell pm revoke com.ichi2.anki.debug android.permission.WRITE_EXTERNAL_STORAGE
    adb shell am compat enable FORCE_ENABLE_SCOPED_STORAGE com.ichi2.anki.debug  > /dev/null
    adb shell am compat enable DEFAULT_SCOPED_STORAGE com.ichi2.anki.debug > /dev/null
    if [ $? == '0' ]; then
        echo "scoped storage enabled: storage access disabled"
    else
        echo "something went wrong: edit script to display errors"
    fi
elif  [ "$1" = "" ]; then
    echo "First argument missing."
    help
else
    echo "unknown argument: '$1'. Valid values: 'limited', 'full'"
    help
fi


# future extension: have an argument trigger the 'permission revoked' state by changing 'deckPath'
# while storage is 'limited'