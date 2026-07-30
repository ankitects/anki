# Brainlift iOS simulator proof

Date: 2026-07-30

## Build identity

- Linked Rust bridge source revision:
  `c3c62ac432e0c44298a9440caa6be1067c1fa5f8-dirty`
- Historical pre-iOS core baseline:
  `af5417a858cf979e4f9cadef02310d197fa52429`
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
  its linked Rust bridge revision.
- The native ABI revision, generated bundle marker, and Swift-visible identity
  agree. Tracked worktree changes are explicit in the `-dirty` suffix.

## Recorded checks

- `cargo test -p anki_ios_bridge`: 8 passed
- `cargo test -p anki brainlift_sync_`: 5 passed
- `BrainliftMobile` simulator suite: 22 passed
- Simulator build, install, and clean launch: passed
- Installed `AnkiBridgeSourceRevision` metadata:
  `c3c62ac432e0c44298a9440caa6be1067c1fa5f8-dirty`
- Simulator XCFramework SHA-256:
  `fd48c6381272495b97359f8156ad9e03850cce4054ded14d4f217e35ebe329de`
- Installed simulator app tree SHA-256:
  `e68451485f12e57b005d0015055d9f821b162d270947e66ab91a702b18555b91`

The app tree checksum is a local Debug simulator artifact checksum, not an
App Store distribution checksum. This proof intentionally records a dirty
artifact: the suffix prevents it from being mistaken for a clean build of the
named commit.

## Explicit limitations

This proof uses the simulator at the user's direction. No claim is made for
physical-device code signing, provisioning, installation, or runtime behavior.
No live AnkiWeb sync was attempted because disposable credentials were not
available. The local Rust sync fixtures are the authoritative sync proof.
