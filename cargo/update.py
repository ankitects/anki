#!/usr/bin/env python3
#
# See README.md

EXTRA_DEPS = [
    'pyo3 = { version = "0.12.3", features = ["extension-module"] }'
]

# If you get a message like the following during a build:
#
# DEBUG: Rule 'raze__reqwest__0_10_8' indicated that a canonical reproducible
# form can be obtained by modifying arguments shallow_since = "1604362745 +1000"

# ...then the commit and shallow_since argument should be added below, as this
# will remove the debug warning, and speed up the git clone.

COMMITS_SHALLOW_SINCE = {
    # reqwest
    "eab12efe22f370f386d99c7d90e7a964e85dd071": "1604362745 +1000",
    # hyper-timeout
    "f9ef687120d88744c1da50a222e19208b4553503": "1604362633 +1000",
    # tokio-io-timeout
    "96e1358555c49905de89170f2b1102a7d8b6c4c2": "1598411535 +1000",
    # prost
    "4ded4a98ef339da0b7babd4efee3fbe8adaf746b": "1598739849 -0700",
    # coarsetime
    "f9e2c86216f0f4803bc75404828318fc206dab29": "1594611111 +1000",
}

import os
import sys
import subprocess
import shutil
import re

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

# write out Cargo.toml
with open("Cargo.toml", "w") as file:
    file.write(header)
    file.write("\n".join(deps))

# update Cargo.lock
subprocess.run(["cargo", "update"], check=True)
input("hit enter to proceed")

# generate cargo-raze files
if os.path.exists("remote"):
    shutil.rmtree("remote")
subprocess.run(["cargo-raze"], check=True)
os.remove("Cargo.toml")

output_lines = []
commit_re = re.compile('\s+commit = "([0-9a-f]+)",')
with open("crates.bzl") as file:
    for line in file.readlines():
        if match := commit_re.match(line):
            commit = match.group(1)
            if commit in line:
                if since := COMMITS_SHALLOW_SINCE.get(commit):
                    output_lines.append(f'        shallow_since = "{since}",\n')
                else:
                    print(f"{commit} not in COMMITS_SHALLOW_SINCE")
        output_lines.append(line)

with open("crates.bzl", "w") as file:
    for line in output_lines:
        file.write(line)
