#!/bin/bash

set -e

test -f rsdroid/build/outputs/aar/rsdroid-release.aar || (
    echo "Run ./build.sh first"
    exit 1
)

. ./set-android-ndk-home.sh

./gradlew rsdroid:lint rsdroid-instrumented:connectedCheck
