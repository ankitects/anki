#!/usr/bin/env bash
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
#
# Usage: test-in-vm.sh [WHEEL]
#
# Runs the smoke test (smoke-test.sh) on an arm64 anki-audio wheel inside a
# macOS 13 (Ventura) virtual machine, to check that the binaries really run
# on the oldest macOS that Anki supports. WHEEL defaults to the newest arm64
# wheel in out/wheels.
#
# Needs an Apple Silicon Mac. Installs Tart (https://tart.run) with Homebrew
# if it is missing, and clones the VM from Cirrus Labs' public image on the
# first run (about 25 GB). Test files are made on this Mac with an ffmpeg from
# PATH, as the VM has none.
#
# Environment:
#   TART_IMAGE     image to clone the VM from
#                  (default: ghcr.io/cirruslabs/macos-ventura-vanilla:latest)
#   TART_VM        name of the VM (default: anki-audio-ventura13)
#   TART_USER      SSH user in the VM (default: admin)
#   TART_PASSWORD  SSH password in the VM (default: admin)
#
# A VM started by this script is stopped again at the end; one that was
# already running is left running.

set -euo pipefail

MACOS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$MACOS_DIR/../../.." && pwd)"

IMAGE="${TART_IMAGE:-ghcr.io/cirruslabs/macos-ventura-vanilla:latest}"
VM="${TART_VM:-anki-audio-ventura13}"
VM_USER="${TART_USER:-admin}"
VM_PASSWORD="${TART_PASSWORD:-admin}"
REMOTE_DIR=/tmp/anki-audio-test

log() {
    echo "==> $*" >&2
}

die() {
    echo "error: $*" >&2
    exit 1
}

[ "$(uname -s)" = Darwin ] && [ "$(uname -m)" = arm64 ] ||
    die "Tart needs an Apple Silicon Mac"
command -v ffmpeg >/dev/null || die "ffmpeg is needed to make the test files (brew install ffmpeg)"

if [ $# -gt 1 ]; then
    echo "usage: $0 [WHEEL]" >&2
    exit 2
fi
if [ $# -eq 1 ]; then
    WHEEL="$1"
else
    WHEEL="$(ls -t "$REPO"/out/wheels/anki_audio-*-macosx_*_arm64.whl 2>/dev/null | head -1 || true)"
    [ -n "$WHEEL" ] || die "no arm64 anki_audio wheel in out/wheels; run qt/audio/build.sh first"
fi
[ -f "$WHEEL" ] || die "$WHEEL not found"
case "$WHEEL" in
*_arm64.whl) ;;
*) die "the VM is arm64, so it can only test an arm64 wheel: $WHEEL" ;;
esac

if ! command -v tart >/dev/null; then
    log "Installing Tart"
    brew install cirruslabs/cli/tart
fi

WORK="$(mktemp -d)"
STARTED_VM=0
cleanup() {
    if [ $STARTED_VM = 1 ]; then
        log "Stopping $VM"
        tart stop "$VM" >/dev/null 2>&1 || true
    fi
    rm -rf "$WORK"
}
trap cleanup EXIT

vm_state() {
    tart list --source local --format json |
        python3 -c 'import json, sys; print(next((vm["State"] for vm in json.load(sys.stdin) if vm["Name"] == sys.argv[1]), ""))' "$VM"
}

state="$(vm_state)"
if [ -z "$state" ]; then
    log "Cloning $IMAGE as $VM (downloads the image on first use)"
    tart clone "$IMAGE" "$VM"
    state="$(vm_state)"
fi
if [ "$state" != running ]; then
    log "Starting $VM"
    tart run --no-graphics "$VM" >"$WORK/tart-run.log" 2>&1 &
    STARTED_VM=1
fi

log "Waiting for the VM's IP address"
IP="$(tart ip --wait 180 "$VM")" || die "VM did not get an IP address"

# Log in with the image's password. OpenSSH asks SSH_ASKPASS for it, so
# neither sshpass nor an installed key is needed.
cat >"$WORK/askpass" <<EOF
#!/bin/sh
echo '$VM_PASSWORD'
EOF
chmod +x "$WORK/askpass"
vm_ssh() {
    SSH_ASKPASS="$WORK/askpass" SSH_ASKPASS_REQUIRE=force \
        ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
        -o LogLevel=ERROR -o ConnectTimeout=5 "$VM_USER@$IP" "$@" </dev/null
}
vm_ssh_stdin() {
    SSH_ASKPASS="$WORK/askpass" SSH_ASKPASS_REQUIRE=force \
        ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
        -o LogLevel=ERROR -o ConnectTimeout=5 "$VM_USER@$IP" "$@"
}

log "Waiting for SSH on $IP"
for _ in $(seq 60); do
    vm_ssh true 2>/dev/null && break
    sleep 2
done
vm_ssh true || die "cannot log in to $VM_USER@$IP"

log "Preparing $(basename "$WHEEL")"
mkdir -p "$WORK/stage"
unzip -q "$WHEEL" 'anki_audio/*' -d "$WORK/stage"
cp "$MACOS_DIR/smoke-test.sh" "$WORK/stage/"
"$MACOS_DIR/smoke-test.sh" --make-media "$WORK/stage/media"

log "Copying to $VM:$REMOTE_DIR"
tar -C "$WORK/stage" -cf - . |
    vm_ssh_stdin "export LC_ALL=C; rm -rf $REMOTE_DIR && mkdir -p $REMOTE_DIR && tar -C $REMOTE_DIR -xf -"

log "Running the smoke test on macOS $(vm_ssh sw_vers -productVersion)"
# LC_ALL avoids perl locale warnings from the host's forwarded LANG.
vm_ssh "export LC_ALL=C; cd $REMOTE_DIR && anki_audio/mpv --version | head -1 && bash smoke-test.sh anki_audio media"
