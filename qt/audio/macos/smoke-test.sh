#!/usr/bin/env bash
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
#
# Usage: smoke-test.sh DIR
#
# Plays short test files with DIR/mpv and encodes a WAV file with DIR/lame.
# DIR is anki_audio/ from an unpacked wheel.

set -euo pipefail

if [ $# -ne 1 ] || [ ! -x "$1/mpv" ] || [ ! -x "$1/lame" ]; then
    echo "usage: $0 DIR (containing mpv and lame)" >&2
    exit 2
fi
command -v ffmpeg >/dev/null || {
    echo "ffmpeg is needed to make the test files" >&2
    exit 2
}

BIN="$(cd "$1" && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
cd "$WORK"

make_file() {
    ffmpeg -nostdin -hide_banner -loglevel error -y "$@"
}

audio=(-f lavfi -i "sine=frequency=440:duration=1")
video=(-f lavfi -i "testsrc2=size=320x240:rate=25:duration=1")

make_file "${audio[@]}" test.wav
make_file "${audio[@]}" -c:a libmp3lame test.mp3
make_file "${audio[@]}" -ac 2 -c:a vorbis -strict experimental test.ogg
make_file "${audio[@]}" -c:a libopus test.opus
make_file "${audio[@]}" -c:a aac test.m4a
make_file "${audio[@]}" -c:a flac test.flac
make_file "${video[@]}" "${audio[@]}" -c:v libvpx-vp9 -c:a libopus -shortest test.webm
make_file "${video[@]}" "${audio[@]}" -c:v libx264 -pix_fmt yuv420p -c:a aac -shortest test.mp4
SVT_LOG=1 make_file "${video[@]}" -c:v libsvtav1 -pix_fmt yuv420p test-av1.mp4

failed=0
for f in test.mp3 test.ogg test.opus test.m4a test.wav test.flac test.webm test.mp4 test-av1.mp4; do
    if "$BIN/mpv" --no-config --ao=null --vo=null --really-quiet "$f"; then
        echo "ok   mpv $f"
    else
        echo "FAIL mpv $f"
        failed=1
    fi
done

# Same arguments as aqt/sound.py uses after recording.
if "$BIN/lame" test.wav out.mp3 --noreplaygain --quiet && test -s out.mp3 &&
    "$BIN/mpv" --no-config --ao=null --really-quiet out.mp3; then
    echo "ok   lame test.wav -> out.mp3"
else
    echo "FAIL lame"
    failed=1
fi

exit $failed
