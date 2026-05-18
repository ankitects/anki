# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""Launches a throwaway Anki instance for Playwright e2e tests.

Playwright's webServer config runs this script and waits until the mediasrv
URL is reachable before starting tests. The instance runs headless via
QT_QPA_PLATFORM=offscreen and binds mediasrv on all interfaces so Playwright's
Chromium can reach it without auth headers.
"""

import os
import signal
import subprocess
import sys
import tempfile

API_PORT = os.environ.get("ANKI_API_PORT", "40000")


def main() -> None:
    with tempfile.TemporaryDirectory() as base_dir:
        env = os.environ.copy()
        env.update(
            {
                "ANKI_BASE": base_dir,
                "ANKI_API_PORT": API_PORT,
                "ANKI_API_HOST": "0.0.0.0",
                "ANKIDEV": "1",
                "ANKI_TEST_MODE": "1",
                "ANKI_SINGLE_INSTANCE_KEY": f"e2e-{API_PORT}",
                "QT_QPA_PLATFORM": "offscreen",
            }
        )

        proc = subprocess.Popen(
            [sys.executable, "tools/run.py"],
            env=env,
        )

        def forward_signal(signum: int, _frame: object) -> None:
            proc.send_signal(signum)

        signal.signal(signal.SIGTERM, forward_signal)
        signal.signal(signal.SIGINT, forward_signal)

        sys.exit(proc.wait())


if __name__ == "__main__":
    main()
