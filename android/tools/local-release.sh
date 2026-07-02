#!/bin/bash
#
# This script assumes a few things -
#
# 1) you are in the main directory of the Anki source code (e.g. 'Anki-Android' is the current working directory)
# 2) you have a Java keystore, with a keystore+key password and key alias

# It will ask you for your keystore and key password

. tools/check-keystore.sh

./gradlew assembleRelease -Duniversal-apk=true
./tools/parallel-package-release.sh TEST
