#!/bin/bash

# Verify pre-requisite
if [ "${ANDROID_HOME}" == "" ]; then
  echo "ANDROID_HOME is unset. This should be set in your shell initialization scripts."
  exit 1
fi

# Set the NDK environment variable to the one we need; might vary from system default
cargo install toml-cli
ANDROID_NDK_VERSION=$(toml get gradle/libs.versions.toml versions.ndk --raw)
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/$ANDROID_NDK_VERSION
if ! [ -e "${ANDROID_NDK_HOME}" ]; then
  echo "Android NDK ${ANDROID_NDK_VERSION} needed for Anki-Android-Backend but not installed."
  echo "Install it with '${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager --install \"ndk;${ANDROID_NDK_VERSION}\"'."
  exit 1
fi