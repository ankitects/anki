#!/usr/bin/env python
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
Standalone launcher for Playwright TS e2e tests.

Seeds a throwaway ANKI_BASE so Anki skips the language picker and the
profile chooser, then spawns Anki with mediasrv pinned to a known local
port. Playwright's webServer config invokes this script and polls an HTTP
page served by mediasrv before letting tests run.

This script duplicates _seed_prefs from qt/tests/conftest.py on purpose so
the pytest harness and the TS harness stay independent. Keep the two copies
in sync if you change the seed schema.
"""

from __future__ import annotations

import os
import pickle
import random
import signal
import sqlite3
import subprocess
import sys
import tempfile
import time
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
MEDIASRV_PORT = int(os.environ.get("ANKI_API_PORT", "40000"))
TEST_PROFILE = "test"


def _seed_prefs(base: Path) -> None:
    meta = {
        "ver": 0,
        "updates": False,
        "created": int(time.time()),
        "id": random.randrange(0, 2**63),
        "lastMsg": 0,
        "suppressUpdate": True,
        "firstRun": False,
        "defaultLang": "en_US",
        # The real switch for setup_auto_update — checked in
        # qt/aqt/main.py:setup_auto_update via pm.check_for_updates().
        # "suppressUpdate" only suppresses a single dismissed version string.
        "check_for_updates": False,
    }
    profile = {
        "mainWindowGeom": None,
        "mainWindowState": None,
        "numBackups": 50,
        "lastOptimize": int(time.time()),
        "searchHistory": [],
        "syncKey": None,
        "syncMedia": True,
        "autoSync": False,
        "allowHTML": False,
        "importMode": 1,
        "lastColour": "#00f",
        "stripHTML": True,
        "deleteMedia": False,
    }
    db_path = base / "prefs21.db"
    conn = sqlite3.connect(str(db_path))
    conn.execute(
        "create table profiles (name text primary key collate nocase, data blob not null)"
    )
    conn.execute(
        "insert into profiles values ('_global', ?)",
        (pickle.dumps(meta, protocol=4),),
    )
    conn.execute(
        "insert into profiles values (?, ?)",
        (TEST_PROFILE, pickle.dumps(profile, protocol=4)),
    )
    conn.commit()
    conn.close()


def main() -> int:
    with tempfile.TemporaryDirectory(prefix="anki-e2e-") as base_str:
        base = Path(base_str)
        _seed_prefs(base)

        env = {
            **os.environ,
            "ANKI_BASE": str(base),
            "ANKI_API_PORT": str(MEDIASRV_PORT),
            "ANKI_SINGLE_INSTANCE_KEY": f"anki-e2e-{base.name}",
            # Documented testing escape: makes _have_api_access() return True
            # for all /_anki/* requests so external clients (Playwright's own
            # Chromium) can hit the API without injecting Authorization
            # headers. Side effect: mediasrv binds to all interfaces. Tolerable
            # on a dev machine; do not enable in shared environments.
            "ANKI_API_HOST": "0.0.0.0",
            "ANKIDEV": "1",
            "PYTHONPYCACHEPREFIX": str(REPO_ROOT / "out" / "pycache"),
            "RUST_BACKTRACE": "1",
            # Headless Qt: the e2e harness only needs mediasrv's HTTP stack,
            # not a visible window. The offscreen platform plugin renders to
            # memory and requires no display server.
            "QT_QPA_PLATFORM": "offscreen",
            # Flush Python output immediately so Playwright captures it.
            "PYTHONUNBUFFERED": "1",
        }
        env.pop("QTWEBENGINE_REMOTE_DEBUGGING", None)
        env.pop("QTWEBENGINE_CHROMIUM_FLAGS", None)
        python = REPO_ROOT / "out" / "pyenv" / "bin" / "python"
        proc = subprocess.Popen(
            [str(python), str(REPO_ROOT / "tools" / "run.py"), "-p", TEST_PROFILE],
            env=env,
        )

        def _forward(signum: int, _frame: object) -> None:
            proc.terminate()

        signal.signal(signal.SIGTERM, _forward)
        signal.signal(signal.SIGINT, _forward)

        try:
            return proc.wait()
        except KeyboardInterrupt:
            proc.terminate()
            return proc.wait()


if __name__ == "__main__":
    sys.exit(main())
