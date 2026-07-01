#!/usr/bin/env bash
# Regenerate Swift protobuf types for the AnkiMCAT app from the repo's protos.
#
# swift-protobuf gives type-safe encode/decode of the request/response messages
# that cross the anki-ios C ABI seam (BackendInit, OpenCollectionRequest,
# ImportAnkiPackageRequest/ImportResponse, GetQueuedCardsRequest/QueuedCards,
# CardAnswer/OpChanges, ...). Hand-built wire bytes don't scale past the trivial
# C2 messages, so we run protoc with the swift plugin over proto/anki/*.proto.
#
# Requirements:
#   * a protoc binary (any 3.x; if <3.15 we pass --experimental_allow_proto3_optional)
#   * protoc-gen-swift built from apple/swift-protobuf (host macOS release build)
#
# Usage:
#   PROTOC=/path/to/protoc PROTOC_GEN_SWIFT=/path/to/protoc-gen-swift ./generate-proto.sh
# Both default to values discovered during the C3/C4 build session.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
PROTO_ROOT="$REPO_ROOT/proto"
OUT="$SCRIPT_DIR/Sources/AnkiMCAT/Proto"

PROTOC="${PROTOC:-/opt/miniconda3/lib/python3.13/site-packages/torch/bin/protoc}"
PROTOC_GEN_SWIFT="${PROTOC_GEN_SWIFT:-/tmp/swift-protobuf-build/.build/release/protoc-gen-swift}"

if [[ ! -x "$PROTOC" ]]; then echo "protoc not found at $PROTOC" >&2; exit 1; fi
if [[ ! -x "$PROTOC_GEN_SWIFT" ]]; then echo "protoc-gen-swift not found at $PROTOC_GEN_SWIFT" >&2; exit 1; fi

# Older protoc (<3.15) needs the flag; newer ones accept it harmlessly.
EXTRA=""
if "$PROTOC" --help 2>&1 | grep -q experimental_allow_proto3_optional; then
  EXTRA="--experimental_allow_proto3_optional"
fi

mkdir -p "$OUT"
find "$OUT" -name '*.pb.swift' -delete

cd "$PROTO_ROOT"
PROTOS=()
for f in anki/*.proto; do PROTOS+=("$f"); done

"$PROTOC" $EXTRA --proto_path=. \
  --plugin=protoc-gen-swift="$PROTOC_GEN_SWIFT" \
  --swift_out="$OUT" \
  --swift_opt=Visibility=Public \
  "${PROTOS[@]}"

echo "Generated $(find "$OUT" -name '*.pb.swift' | wc -l | tr -d ' ') Swift protobuf files under $OUT/anki/"
