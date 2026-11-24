#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later

"""Run emulator tests and generate the coverage report."""

import hashlib
from pathlib import Path
import subprocess
import sys
import time


def read_coverage():
    shell = ["adb", "shell", "-T", "run-as", "com.ichi2.anki"]
    for attempt in range(1, 6):
        try:
            checksum = subprocess.check_output([*shell, "sha256sum", "coverage.ec"], timeout=30).split()
            data = subprocess.check_output([*shell, "cat", "coverage.ec"], timeout=30)
            # ADB can exit successfully after a truncated transfer.
            if not data or not checksum or hashlib.sha256(data).hexdigest().encode() != checksum[0]:
                raise ValueError("Coverage copy is empty or incomplete")
            return data
        except (subprocess.SubprocessError, ValueError) as error:
            # TODO (Issue 22147 - Dec 2026): check CI logs for "Coverage copy attempt" failures.
            print(f"Coverage copy attempt {attempt}/5 failed: {error}", file=sys.stderr)
            if attempt == 5:
                raise
            time.sleep(1)


def main():
    connected_test = ":AnkiDroid:connectedPlayReleaseAndroidTest"
    subprocess.run(
        ["./gradlew", "uninstallAll", connected_test,
         "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true", "--daemon"],
        check=True,
    )
    data = read_coverage()
    output_dir = Path("AnkiDroid/build/outputs/code_coverage/playReleaseAndroidTest/connected")
    output_dir.mkdir(parents=True, exist_ok=True)
    # JaCoCo reads every .ec file, including AGP's potentially truncated copy.
    for previous in output_dir.rglob("*.ec"):
        previous.unlink()
    (output_dir / "coverage.ec").write_bytes(data)
    subprocess.run(
        ["./gradlew", ":AnkiDroid:jacocoAndroidTestReport", "-x", connected_test, "--daemon"],
        check=True,
    )


if __name__ == "__main__":
    try:
        main()
    except subprocess.CalledProcessError as error:
        sys.exit(error.returncode)
    except (OSError, ValueError, subprocess.TimeoutExpired) as error:
        sys.exit(str(error))
