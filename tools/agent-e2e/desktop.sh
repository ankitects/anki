#!/usr/bin/env bash
set -euo pipefail
exec > /artifacts/desktop.log 2>&1
mkdir -p "$XDG_RUNTIME_DIR" /artifacts/profile
chmod 700 "$XDG_RUNTIME_DIR"
touch /root/.Xauthority
Xvfb :99 -screen 0 1280x900x24 -ac -nolisten tcp &
until xdpyinfo > /dev/null 2>&1; do sleep 0.1; done
openbox &
# Record at 5× speed; finish the video when Docker stops the desktop.
ffmpeg -loglevel error -nostdin -y -f x11grab -video_size 1280x900 -framerate 8 -i :99 \
    -vf 'setpts=0.2*PTS' -r 40 -c:v libx264 -preset ultrafast -crf 28 -g 40 \
    -movflags +frag_keyframe+empty_moov /artifacts/replay-5x.mp4 &
recorder=$!
trap 'kill -INT "$recorder"; wait "$recorder"' EXIT
trap 'exit 0' TERM
x11vnc -display :99 -forever -shared -nopw -localhost -rfbport 5900 &
websockify --web=/usr/share/novnc 6080 localhost:5900 &
pulseaudio --start --exit-idle-time=-1
pactl load-module module-null-sink sink_name=e2e
anki -b /artifacts/profile &
timeout 60 xdotool search --sync --onlyvisible --pid "$!"
touch /artifacts/.ready
# Anki or any desktop service exiting ends the test.
wait -n
exit 1
