#!/bin/bash

# This script expects to be run from the tools directory in
# a local checkout of Anki-Android-Backend, e.g.
# git clone https://github.com/ankidroid/Anki-Android-Backend
# cd Anki-Android-Backend/tools

# Update the actions runner if needed?
# latest release and instructions here:
# https://github.com/actions/runner/releases

# Install rustup, but empty
#curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y --default-toolchain none

# Make sure our submodules are up to date

# Install the Rust toolchains we need
for RUST_TC in `find .. |grep rust-toolchain.toml`; do rustup toolchain install `grep channel $RUST_TC | cut -f2 -d'"'`; done

# we need protobuf
brew install protobuf
export PROTOC=`which protoc`

# install the JDK we need
brew install temurin@21

# install the NDK we need
cargo install toml-cli
export ANDROID_NDK_VERSION=$(toml get ../gradle/libs.versions.toml versions.ndk --raw)
export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin"
../.github/scripts/install_ndk.sh ${ANDROID_NDK_VERSION}
export ANDROID_NDK_LATEST_HOME="${ANDROID_SDK_ROOT}/ndk/${ANDROID_NDK_VERSION}"
export ANDROID_NDK_HOME=$ANDROID_NDK_LATEST_HOME

# install our cross compilers
# TODO - for BREWCMD in `grep "brew install" ../.github/workflows/build-release.yml`; do $BREWCMD; done
brew install mingw-w64 
brew install MaterializeInc/crosstools/x86_64-unknown-linux-gnu

# install anki-specific build tool N2
../anki/tools/install-n2

# Warm up local caches with a run of workflow build commands
cd ..

# from build-quick.yml
cargo run -p build_rust
./check-rust.sh
./gradlew test rsdroid:lint

# from build-release.yml
export ALL_ARCHS=1
export RELEASE=1
export CARGO_PROFILE_RELEASE_LTO=fat
./build.sh
