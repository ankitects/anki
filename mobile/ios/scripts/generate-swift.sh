#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
IOS_ROOT="$REPO_ROOT/mobile/ios"
GENERATED="$IOS_ROOT/BrainliftMobile/Backend/Generated"
PROTOC="${PROTOC:-$(command -v protoc)}"
PROTOC_GEN_SWIFT="${PROTOC_GEN_SWIFT:-$(command -v protoc-gen-swift)}"

if [[ -z "$PROTOC" || -z "$PROTOC_GEN_SWIFT" ]]; then
  echo "protoc and protoc-gen-swift are required" >&2
  exit 1
fi

mkdir -p "$GENERATED"
find "$GENERATED" -type f -name '*.pb.swift' -delete

"$PROTOC" \
  --plugin="protoc-gen-swift=$PROTOC_GEN_SWIFT" \
  --proto_path="$REPO_ROOT/proto" \
  --swift_opt=Visibility=Internal \
  --swift_out="$GENERATED" \
  "$REPO_ROOT"/proto/anki/*.proto

cd "$REPO_ROOT"
cargo build --locked -p anki_ios_bridge

echo "Generated Swift protobufs and backend method addresses in $GENERATED"
