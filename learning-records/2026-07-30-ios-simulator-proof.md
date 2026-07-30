# Brainlift iOS simulator proof

Date: 2026-07-30

## Build identity

- Anki core commit: `af5417a858cf979e4f9cadef02310d197fa52429`
- Bundle identifier: `com.techmexdev.BrainliftMobile`
- Xcode: 26.6 (`17F113`)
- Rust: 1.89.0
- XcodeGen: 2.46.0
- Swift Protobuf: 1.38.1
- Simulator: iPhone 17 Pro, iOS 26.5

## Verified behavior

- The Rust bridge owns opaque backend handles and response buffers and contains
  panics at the C boundary.
- Swift opens a real fixture collection through the bridge.
- Review queue, rendering, grading, undo, persistence, evidence, and sync calls
  cross the Rust boundary.
- Evidence values are direct projections of Rust responses.
- Initial sync auto-accepts only a backend-requested full download. Upload and
  later full-sync directions require explicit confirmation.
- The generated app installs and launches on the simulator, and visibly reports
  its Anki core revision.

## Recorded checks

- `cargo test -p anki_ios_bridge`: 7 passed
- `cargo test -p anki brainlift_sync_`: 5 passed
- `BrainliftMobile` simulator suite: 22 passed
- Simulator build, install, and clean launch: passed
- Installed `AnkiCoreCommit` metadata:
  `af5417a858cf979e4f9cadef02310d197fa52429`
- Simulator XCFramework SHA-256:
  `38907f7fd5e6ddee357d1480dbb47b11e5c528c32d589e4ee358286870d0af95`
- Installed simulator app tree SHA-256:
  `a56d4f10e13576d0bda9548970132d6248ef9939a9e4f7099ff87f1a657d1729`

The app tree checksum is a local Debug simulator artifact checksum, not an
App Store distribution checksum.

## Explicit limitations

This proof uses the simulator at the user's direction. No claim is made for
physical-device code signing, provisioning, installation, or runtime behavior.
No live AnkiWeb sync was attempted because disposable credentials were not
available. The local Rust sync fixtures are the authoritative sync proof.
