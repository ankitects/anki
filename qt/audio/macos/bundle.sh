#!/usr/bin/env bash
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
#
# Copies mpv and lame from the build prefix to out/extracted, in the layout
# that hatch_build.py expects, and rewrites mpv's library install names to
# @executable_path/libs/.

# shellcheck source=common.sh
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

OUTPUT_DIR="$REPO/out/extracted"

rm -rf "${OUTPUT_DIR:?}/mpv" "${OUTPUT_DIR:?}/lame"
mkdir -p "$OUTPUT_DIR/mpv/libs" "$OUTPUT_DIR/lame"

cp "$PREFIX/bin/mpv" "$OUTPUT_DIR/mpv/"
chmod u+w "$OUTPUT_DIR/mpv/mpv"
dylibbundler -x "$OUTPUT_DIR/mpv/mpv" -d "$OUTPUT_DIR/mpv/libs" \
    -p @executable_path/libs/ -b -s "$PREFIX/lib"

# dylibbundler rewrites both of mpv's Swift rpaths (the Xcode toolchain and
# /usr/lib/swift) to @executable_path/libs/. dyld refuses to load a binary
# with a duplicate LC_RPATH if it was built with a recent SDK, so keep one.
dedupe_rpaths() {
    local file=$1 rpath changed=0
    for rpath in $(otool -l "$file" | awk '/cmd LC_RPATH$/ { r = 1; next } /cmd / { r = 0 } r && $1 == "path" { print $2 }' | sort | uniq -d); do
        while [ "$(otool -l "$file" | grep -cF " path $rpath ")" -gt 1 ]; do
            install_name_tool -delete_rpath "$rpath" "$file" 2>/dev/null
            changed=1
        done
    done
    if [ $changed = 1 ]; then
        codesign --force --sign - "$file"
    fi
}
for file in "$OUTPUT_DIR/mpv/mpv" "$OUTPUT_DIR/mpv/libs"/*.dylib; do
    dedupe_rpaths "$file"
done

cp "$PREFIX/bin/lame" "$OUTPUT_DIR/lame/"
chmod u+w "$OUTPUT_DIR/lame/lame"

log "Bundled into $OUTPUT_DIR"
ls "$OUTPUT_DIR/mpv/libs"
