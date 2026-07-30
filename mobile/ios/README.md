# Brainlift iOS companion

Brainlift is a SwiftUI companion app backed by Anki's Rust core. The Swift layer
owns presentation and credentials; review, scheduling, evidence, and sync
decisions remain in Rust.

## Requirements

- Xcode 26.6 or newer
- Rust toolchain with `aarch64-apple-ios-sim` and, for device builds,
  `aarch64-apple-ios`
- XcodeGen 2.46 or newer
- Protocol Buffers compiler
- Swift Protobuf compiler plugin 1.38.1

With Homebrew, install the non-Rust tools with:

```sh
brew install xcodegen swift-protobuf protobuf
rustup target add aarch64-apple-ios-sim aarch64-apple-ios
```

## Generate and run

From the repository root:

```sh
mobile/ios/scripts/generate-project.sh
open mobile/ios/BrainliftMobile.xcodeproj
```

`generate-project.sh` regenerates Swift protobuf sources and backend method
addresses, builds a simulator-only XCFramework, and regenerates the Xcode
project. Choose the `BrainliftMobile` scheme and an iOS simulator.

To build the XCFramework with both simulator and physical-device slices:

```sh
mobile/ios/scripts/build-xcframework.sh
```

Signing remains an Xcode-local concern. Configure the development team and a
provisioning profile before installing on a physical device.

## Verify

```sh
cargo test -p anki_ios_bridge
cargo test -p anki brainlift_sync_
```

Run the `BrainliftMobile` scheme's tests in Xcode for the Swift and
Rust-through-FFI integration suite. The app displays its embedded Anki core
revision beside the Evidence heading.

The generated XCFramework checksum is written to:

```text
out/ios/AnkiBackend.xcframework.sha256
```
