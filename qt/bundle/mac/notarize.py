#!/usr/bin/env python
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
import re
import subprocess
import sys
import time
from pathlib import Path

USERNAME = os.getenv("NOTARIZE_USER")
PASSWORD = os.getenv("NOTARIZE_PASSWORD")
BUNDLE_ID = "net.ankiweb.dtop"


def upload(base_dir: Path, uuid_path: Path) -> None:
    print("--- Prepare notarization zip")

    app_dir = base_dir / "Anki.app"
    zip_path = app_dir.with_suffix(".zip")

    subprocess.run(["ditto", "-c", "-k", "--keepParent", app_dir, zip_path])

    print("--- Upload for notarization")

    try:
        output = subprocess.check_output(
            [
                "xcrun",
                "altool",
                "--notarize-app",
                "--primary-bundle-id",
                BUNDLE_ID,
                "--username",
                USERNAME,
                "--password",
                PASSWORD,
                "--file",
                zip_path,
            ],
            stderr=subprocess.STDOUT,
            encoding="utf8",
        )
    except subprocess.CalledProcessError as e:
        print("error uploading:", e.output)
        sys.exit(1)

    uuid = None
    for line in output.splitlines():
        m = re.search(r"RequestUUID = (.*)", line)
        if m:
            uuid = m.group(1)

    if not uuid:
        print("no uuid found - upload output:")
        print(output)
        sys.exit(1)

    open(uuid_path, "w").write(uuid)
    zip_path.unlink()


def _extract_status(output):
    for line in output.splitlines():
        m = re.search(r"Status: (.*)", line)
        if m:
            return m.group(1)

    return None


def wait_for_success(uuid, wait=True):
    while True:
        print("checking status...", end="", flush=True)

        try:
            output = subprocess.check_output(
                [
                    "xcrun",
                    "altool",
                    "--notarization-info",
                    uuid,
                    "--username",
                    USERNAME,
                    "--password",
                    PASSWORD,
                ],
                stderr=subprocess.STDOUT,
                encoding="utf8",
            )
        except subprocess.CalledProcessError as e:
            print("error checking status:")
            print(e.output)
            sys.exit(1)

        status = _extract_status(output)
        if status is None:
            print("could not extract status:")
            print(output)
            sys.exit(1)

        if status == "invalid":
            print("notarization failed:")
            print(output)
            sys.exit(1)

        if status == "success":
            print("success!")
            print(output)
            return

        print(status)
        if not wait:
            return
        time.sleep(30)


def staple(app_path):
    try:
        subprocess.check_call(
            [
                "xcrun",
                "stapler",
                "staple",
                app_path,
            ]
        )
    except subprocess.CalledProcessError as e:
        print("error stapling:")
        print(e.output)
        sys.exit(1)


cmd = sys.argv[1]
base_dir = Path(sys.argv[2])
uuid_path = base_dir / "uuid"

if cmd == "upload":
    upload(base_dir, uuid_path)
elif cmd == "status":
    uuid = open(uuid_path).read()
    wait_for_success(uuid, False)
elif cmd == "staple":
    uuid = open(uuid_path).read()
    wait_for_success(uuid)
    staple(base_dir / "Anki.app")
    uuid_path.unlink()
