#!/usr/bin/env python3
#
# Fetches dependencies from ../rslib/Cargo.toml, adds pyo3
# for rsbridge, runs cargo update, then outputs the dependencies
# as Bazel targets.
#

EXTRA_DEPS = [
    # when updating, the version number in raze.toml will need updating too
    'pyo3 = { version = "0.12.0", features = ["extension-module"] }'
]

import os
import sys
import subprocess
import shutil

if os.getcwd() != os.path.abspath(os.path.dirname(__file__)):
    print("Run this from the cargo/ folder")
    sys.exit(1)

with open("raze.toml") as file:
    header = file.read()

deps = []
with open("../rslib/Cargo.toml") as file:
    started = False
    for line in file.readlines():
        if line.startswith("# BEGIN DEPENDENCIES"):
            started = True
            continue
        if not started:
            continue
        deps.append(line.strip())

deps.extend(EXTRA_DEPS)

with open("Cargo.toml", "w") as file:
    file.write(header)
    file.write("\n".join(deps))

subprocess.run(["cargo", "update"], check=True)
if os.path.exists("remote"):
    shutil.rmtree("remote")
subprocess.run(["cargo-raze"], check=True)
os.remove("Cargo.toml")
