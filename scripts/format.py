# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
This is an experiment with running some of the formatters outside
of Bazel' testing system. It means we pick up files not explictly
listed as inputs, but also means that work is done even when files
have not changed, and it does not work on Windows.
"""

import os
import subprocess
import sys
import time
from pathlib import Path

workspace = Path(os.environ["BUILD_WORKSPACE_DIRECTORY"])
binroot = workspace / "bazel-bin"
pyroot = binroot / "pip"
tsroot = workspace / "ts"
tsbinroot = tsroot / "node_modules" / ".bin"

if sys.platform.startswith("win32"):
    binext = ".exe"
else:
    binext = ""

# pyargs = ["--check"]
# prettierargs = "--check"
pyargs = []
prettierargs = "--write"


def taken(stamp):
    ms = int((time.time() - stamp) * 1000.0)
    return f"{ms}ms"


failed = False

print("* Fixing Python formatting...", end="", flush=True)
t = time.time()
if subprocess.run(
    [
        str(pyroot / ("black" + binext)),
    ]
    + pyargs
    + [
        "-t",
        "py38",
        "--exclude='qt/aqt/forms|_gen|bazel-|node_modules'",
        "-q",
        ".",
    ],
    cwd=workspace,
).returncode:
    failed = True
print(taken(t))

print("* Fixing Python imports...", end="", flush=True)
t = time.time()
if subprocess.run(
    [
        str(pyroot / ("isort" + binext)),
    ]
    + pyargs
    + [
        "--settings-path",
        "pip/.isort.cfg",
        "--skip",
        "forms",
        "--sg",
        "*_gen.py",
        "--skip",
        "generated.py",
        "-q",
        ".",
    ],
    cwd=workspace,
).returncode:
    failed = True
print(taken(t))

print("* Fixing Typescript formatting...", end="\n", flush=True)
t = time.time()
if subprocess.run(
    [
        tsbinroot / ("prettier" + binext),
        "--config",
        tsroot / ".prettierrc",
        "--ignore-path",
        tsroot / ".prettierignore",
        "--loglevel",
        "silent",
        prettierargs,
        "ts",
        "qt",
    ],
    cwd=workspace,
).returncode:
    failed = True
print(taken(t))

print("* Fixing Fluent formatting...", end="", flush=True)
t = time.time()
if subprocess.run(
    [
        binroot / "ftl" / ("format" + binext),
    ],
    cwd=workspace,
).returncode:
    failed = True
print(taken(t))


if failed:
    sys.exit(1)
