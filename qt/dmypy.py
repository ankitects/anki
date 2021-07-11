# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
Helper to run mypy in daemon mode. See development.md. Windows is not
currently supported.
"""

import os
import subprocess
import sys
import time
from pathlib import Path

workspace = Path(os.environ["BUILD_WORKSPACE_DIRECTORY"])
binroot = workspace / "bazel-bin"
pyroot = binroot / "pip"

if sys.platform.startswith("win32"):
    binext = ".exe"
else:
    binext = ""

if subprocess.run(
    [
        str(pyroot / ("dmypy" + binext)),
    ]
    + [
        "run",
        "--",
        "--config-file",
        "qt/mypy.ini",
        "bazel-bin/qt/dmypy.runfiles/ankidesktop/pylib/anki",
        "bazel-bin/qt/dmypy.runfiles/ankidesktop/qt/aqt",
        "--python-executable",
        os.path.abspath("pip/stubs/extendsitepkgs"),
    ],
    env={
        "MYPYPATH": "bazel-bin/qt/dmypy.runfiles/pyqt5",
        "EXTRA_SITE_PACKAGES": os.path.abspath(os.getenv("EXTRA_SITE_PACKAGES")),
    },
    cwd=workspace,
).returncode:
    sys.exit(1)
