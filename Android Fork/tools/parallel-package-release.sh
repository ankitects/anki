#!/bin/bash
#
# This script assumes a few things -
#
# 1) you are in the main directory of the Anki source code (e.g. 'Anki-Android' is the current working directory)
# 2) you have a Java keystore, with a keystore+key password and key alias
# 3) you have no local changes in your working directory (e.g. "git reset --hard && git clean -f")
# If those assumptions are met, this script will generate 3 parallel builds that should be the same as your current checkout
# They will be placed in the parent directory ('..') as 'AnkiDroid-<version>.parallel.<A B or C>.apk'

# It takes 1 argument - the tag name to use for the build (e.g. '2.9alpha16')
# It will ask you for your keystore and key password

TAG=$1
if [ "$TAG" == "" ]; then
    echo "Please enter a tag (likely a version number) for the APK file names"
    exit 1
fi

. tools/check-keystore.sh

# Inherit from release.sh when invoked via it; otherwise compute our own so
# every APK in this run shares one BUILD_TIME.
BUILD_TIME_MS="${BUILD_TIME_MS:-$(date +%s000)}"
export BUILD_TIME_MS

# Get on to the tag requested
#git checkout $TAG

BUILDNAMES='A B C D E'
for BUILD in $BUILDNAMES; do
    LCBUILD=`tr '[:upper:]' '[:lower:]' <<< $BUILD`
    ./gradlew --stop
    if ! ./gradlew assembleFullRelease -PcustomSuffix="$LCBUILD" -PcustomName="AnkiDroid.$BUILD" -Duniversal-apk=true -PbuildTime="$BUILD_TIME_MS"
    then
      echo "Unable to build parallel target $BUILD"
      exit 1
    fi
    cp AnkiDroid/build/outputs/apk/full/release/AnkiDroid-full-universal-release.apk ./AnkiDroid-$TAG.parallel.$BUILD.apk
done
git reset --hard
git clean -f
