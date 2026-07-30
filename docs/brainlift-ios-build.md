# Brainlift iOS build architecture

The iOS app is a thin SwiftUI client over the existing Anki Rust backend.

- `mobile/ios/rust_bridge` exposes a panic-contained C ABI with opaque backend
  handles and explicitly owned response buffers.
- Generated Swift protobuf types and backend method addresses keep Swift calls
  aligned with Rust's service definitions.
- `AnkiBackend` serializes calls through a Swift actor.
- Review, scheduling, evidence, and sync semantics are calculated in Rust.
- Swift stores sync credentials in Keychain and renders backend results.

The app bundle identifier is `com.techmexdev.BrainliftMobile`. Build metadata
and the Evidence panel identify the embedded Anki core revision as
`af5417a858cf979e4f9cadef02310d197fa52429`, the last core commit before the iOS
companion commits.

See [`mobile/ios/README.md`](../mobile/ios/README.md) for prerequisites,
generation, build, and verification commands.

## Scope of current proof

The recorded proof is simulator-only. It validates Rust bridge tests, Swift
unit and Rust-through-FFI integration tests, sync conflict fixtures, generation,
installation, launch, and visible build identity on an iPhone simulator.

It does not claim:

- Apple code signing or installation on physical hardware
- a device-architecture build produced during the simulator proof
- a live AnkiWeb sync, because no disposable account credentials were supplied
