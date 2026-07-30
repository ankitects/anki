# Brainlift iOS simulator proof

Date: 2026-07-30

## Build identity

- Linked Rust bridge source revision:
  `0e3627234f9823ebafadfde56a58c5d2dbed8b00`
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
  `0e3627234f9823ebafadfde56a58c5d2dbed8b00`
- Simulator XCFramework SHA-256:
  `5174f53ed44ae7f0264c904b0039ed8493b979f7f1830af217860c55fe134169`
- Installed simulator app tree SHA-256:
  `a879996a872a9383b1e7db3d9ed512ab654341bfb2f210ace97b68e471eef118`

The app tree checksum is a local Debug simulator artifact checksum, not an
App Store distribution checksum. The artifact was generated from the clean
source revision above; this proof-record update follows that artifact commit.

## Explicit limitations

This proof uses the simulator at the user's direction. No claim is made for
physical-device code signing, provisioning, installation, or runtime behavior.
No live AnkiWeb sync was attempted because disposable credentials were not
available. The local Rust sync fixtures are the authoritative sync proof.
