# Brainlift iOS simulator proof

Date: 2026-07-30

## Build identity

- Linked Rust bridge source revision:
  `ef2099bdcbb7bbf4e505d57a07bfb8e0270d5437`
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
- Card HTML renders under a restrictive content policy with remote navigation
  and exfiltration channels blocked.
- Evidence values are direct projections of Rust responses.
- Initial sync auto-accepts only a backend-requested full download. Upload and
  later full-sync directions require explicit confirmation.
- Sync progress remains observable during long native operations, while close
  waits safely for active bridge calls.
- Simulator UI tests cover review/reveal/grade/undo, evidence abstention, and
  explicit later full-sync direction selection.
- The generated app installs and launches on the simulator, and visibly reports
  its linked Rust bridge revision.
- The native ABI revision, generated bundle marker, and Swift-visible identity
  agree, and the recorded source revision is a clean committed tree.

## Recorded checks

- `cargo test -p anki_ios_bridge`: 9 passed
- `cargo test -p anki brainlift_sync_`: 5 passed
- `BrainliftMobile` simulator suite: 37 passed (34 unit/integration, 3 UI)
- Simulator build, install, and clean launch: passed
- Installed `AnkiBridgeSourceRevision` metadata:
  `ef2099bdcbb7bbf4e505d57a07bfb8e0270d5437`
- Simulator XCFramework SHA-256:
  `23938cb1e543e35a1c83d7c888c7b81c6ac410f7536ba07a091b97a9ee084f03`
- Installed simulator app tree SHA-256:
  `6f6172c5e5ab15da2d3d5df02e0105d616c8b898db09a7597c7ef9b2ea30375e`

The app tree checksum is a local Debug simulator artifact checksum, not an
App Store distribution checksum. The artifact was generated from the clean
source revision above; this proof-record update follows that artifact commit.

## Explicit limitations

This proof uses the simulator at the user's direction. No claim is made for
physical-device code signing, provisioning, installation, or runtime behavior.
No live AnkiWeb sync was attempted because disposable credentials were not
available. The local Rust sync fixtures are the authoritative sync proof.
