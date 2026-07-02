#!/usr/bin/env bash

set -e # Error out if there were any problems

red=31
green=32
yellow=33
blue=34
purple=35
cyan=36
lgray=37
gray=90
normal=0
bold=1

cecho() {
  echo -e "\033[$bold;$1m$2\033[0m"
}

error_echo() {
  cecho $red "✗ $1"
  exit 1
}

ok_echo() {
  cecho $green "✓ $1"
}

cecho $lgray"##################################"
cecho $lgray "# AnkiDroidBackend Doctor Script #"
cecho $lgray "##################################\n"

cecho $lgray "Getting anki submodules"
git submodule update --init

which cargo || (
  echo "Rustup should be installed"
  exit 1
)

if [[ -n "$ANDROID_SDK_ROOT" ]]; then
  ok_echo "ANDROID_SDK_ROOT is set"
else
  error_echo "ANDROID_SDK_ROOT should point to your SDK installation"
fi

cargo install toml-cli
ANDROID_NDK_VERSION=$(toml get gradle/libs.versions.toml versions.ndk --raw)

if [[ -d "$ANDROID_SDK_ROOT/ndk/$ANDROID_NDK_VERSION" ]]; then
  ok_echo "NDK $ANDROID_NDK_VERSION directory found"
else
  echo -e "Expected to find: $ANDROID_SDK_ROOT/ndk/$ANDROID_NDK_VERSION"
  error_echo "NDK $ANDROID_NDK_VERSION directory not found"
fi

if [ "$(uname)" == "Darwin" ]; then
  # We do not want to run under Rosetta 2
  arch_name="$(uname -m)"
  if [ "${arch_name}" = "x86_64" ]; then
    if [ "$(sysctl -in sysctl.proc_translated)" = "1" ]; then
      echo "Running on Rosetta 2"
      echo "This is not supported. Run \`env /usr/bin/arch -arm64 /bin/bash --login\` then try again"
      exit 1
    else
      echo "Running on native Intel"
    fi
  elif [ "${arch_name}" = "arm64" ]; then
    echo "Running on ARM, installing Apple Silicon M1 target for robolectric dylib generation"
    rustup target add aarch64-apple-darwin  # apple silicon for M1 robolectric test dylib generation
  else
    echo "Unknown architecture: ${arch_name}"
  fi
fi
