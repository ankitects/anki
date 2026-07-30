#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJ_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
OUTPUT_DIR="$PROJ_ROOT/out/brainlift-installer"
APP="$OUTPUT_DIR/Anki Brainlift.app"
DMG="$OUTPUT_DIR/Anki-Brainlift.dmg"
VERSION="$(tr -d '[:space:]' < "$PROJ_ROOT/.version")"
COMMIT="$(git -C "$PROJ_ROOT" rev-parse HEAD)"

if [[ -n "$(git -C "$PROJ_ROOT" status --porcelain --untracked-files=no)" ]]; then
    echo "Refusing to package tracked changes that are not recorded in $COMMIT" >&2
    exit 1
fi

cd "$PROJ_ROOT"
rm -f "$PROJ_ROOT"/out/wheels/anki-*.whl "$PROJ_ROOT"/out/wheels/aqt-*.whl
PROTOC="${PROTOC:-/opt/homebrew/bin/protoc}" ./ninja wheels launcher:uv_universal

ANKI_WHEELS=("$PROJ_ROOT"/out/wheels/anki-*.whl)
AQT_WHEELS=("$PROJ_ROOT"/out/wheels/aqt-*.whl)
if [[ ${#ANKI_WHEELS[@]} -ne 1 ]] || [[ ${#AQT_WHEELS[@]} -ne 1 ]]; then
    echo "Expected exactly one Anki wheel and one AQT wheel for version $VERSION" >&2
    exit 1
fi

ANKI_WHEEL="${ANKI_WHEELS[0]}"
AQT_WHEEL="${AQT_WHEELS[0]}"
ANKI_WHEEL_NAME="$(basename "$ANKI_WHEEL")"
AQT_WHEEL_NAME="$(basename "$AQT_WHEEL")"
PACKAGE_VERSION="${ANKI_WHEEL_NAME#anki-}"
PACKAGE_VERSION="${PACKAGE_VERSION%%-*}"

rm -rf "$OUTPUT_DIR"
mkdir -p \
    "$APP/Contents/MacOS" \
    "$APP/Contents/Resources/runtime/wheels"

sed "s/ANKI_VERSION/$VERSION/g" "$SCRIPT_DIR/Info.plist" > "$APP/Contents/Info.plist"
/usr/libexec/PlistBuddy -c "Set :CFBundleDisplayName Anki Brainlift" "$APP/Contents/Info.plist"
/usr/libexec/PlistBuddy -c "Set :CFBundleName Anki Brainlift" "$APP/Contents/Info.plist"
/usr/libexec/PlistBuddy -c "Set :CFBundleExecutable brainlift-launcher" "$APP/Contents/Info.plist"
/usr/libexec/PlistBuddy -c "Set :CFBundleIdentifier dev.techmex.brainlift" "$APP/Contents/Info.plist"

cp "$SCRIPT_DIR/icon/Assets.car" "$APP/Contents/Resources/"
cp "$PROJ_ROOT/out/launcher/uv" "$APP/Contents/Resources/uv"
cp "$SCRIPT_DIR/brainlift-launcher.sh" "$APP/Contents/MacOS/brainlift-launcher"
cp "$ANKI_WHEEL" "$APP/Contents/Resources/runtime/wheels/"
cp "$AQT_WHEEL" "$APP/Contents/Resources/runtime/wheels/"
cp "$PROJ_ROOT/.python-version" "$APP/Contents/Resources/runtime/"
printf '%s\n' "$COMMIT" > "$APP/Contents/Resources/brainlift-commit.txt"
chmod +x "$APP/Contents/MacOS/brainlift-launcher" "$APP/Contents/Resources/uv"

cat > "$APP/Contents/Resources/runtime/pyproject.toml" <<EOF
[project]
name = "anki-brainlift-installer"
version = "$PACKAGE_VERSION"
requires-python = ">=3.13,<3.14"
dependencies = [
  "anki==$PACKAGE_VERSION",
  "aqt[audio,qt]==$PACKAGE_VERSION",
]

[tool.uv]
package = false

[tool.uv.sources]
anki = { path = "wheels/$ANKI_WHEEL_NAME" }
aqt = { path = "wheels/$AQT_WHEEL_NAME" }
EOF

"$APP/Contents/Resources/uv" lock --project "$APP/Contents/Resources/runtime"

codesign --force --deep --sign - "$APP"
codesign --verify --deep --strict "$APP"

if [[ "${BRAINLIFT_SKIP_SMOKE:-0}" != "1" ]]; then
    SMOKE_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/anki-brainlift-smoke.XXXXXX")"
    trap 'rm -rf "$SMOKE_ROOT"' EXIT
    BRAINLIFT_INSTALL_ROOT="$SMOKE_ROOT" \
        BRAINLIFT_INSTALLER_SMOKE_ONLY=1 \
        "$APP/Contents/MacOS/brainlift-launcher"
fi

hdiutil create \
    -volname "Anki Brainlift" \
    -srcfolder "$APP" \
    -ov \
    -format UDZO \
    "$DMG"

shasum -a 256 "$DMG" "$ANKI_WHEEL" "$AQT_WHEEL" > "$OUTPUT_DIR/SHA256SUMS"
printf 'App: %s\nDMG: %s\nCommit: %s\n' "$APP" "$DMG" "$COMMIT"
