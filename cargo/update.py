#!/usr/bin/env python3
#
# See README.md

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
}

import os
import sys
import subprocess
import shutil
import re
import glob

if os.getcwd() != os.path.abspath(os.path.dirname(__file__)):
    print("Run this from the cargo/ folder")
    sys.exit(1)


def update_cargo_lock():
    # update Cargo.lock
    subprocess.run(["cargo", "update"], check=True)


def run_cargo_raze():
    # generate cargo-raze files
    subprocess.run(["cargo-raze"], cwd="..", check=True)


def write_licenses():
    # dump licenses
    result = subprocess.check_output(["cargo-license", "-j"], cwd="../rslib")
    with open("licenses.json", "wb") as file:
        file.write(result)

    # export license file
    with open("BUILD.bazel", "a", encoding="utf8") as file:
        file.write(
            """
exports_files(["licenses.json"])
"""
        )


def update_crates_bzl():
    output_lines = []
    commit_re = re.compile(r'\s+commit = "([0-9a-f]+)",')
    reqwest_build_prefix = re.compile(r"/remote:BUILD.reqwest-\d+\.\d+\.\d+")

    with open("crates.bzl") as file:
        for line in file.readlines():
            # update shallow-since references for git crates
            if match := commit_re.match(line):
                commit = match.group(1)
                if commit in line:
                    if since := COMMITS_SHALLOW_SINCE.get(commit):
                        output_lines.append(f'        shallow_since = "{since}",\n')
                    else:
                        print(f"{commit} not in COMMITS_SHALLOW_SINCE")

            # use our custom reqwest build file
            if match := reqwest_build_prefix.search(line):
                line = line.replace(match.group(0), ":BUILD.reqwest")

            output_lines.append(line)

    with open("crates.bzl", "w") as file:
        for line in output_lines:
            file.write(line)


def generated_reqwest_build_file():
    return glob.glob("remote/*reqwest-*")[0]


def update_reqwest_deps():
    "Update version numbers in our custom build file."
    dep_with_version = re.compile(r"@raze__(.+?)__([\d_]+)//")

    version_map = {}
    with open(generated_reqwest_build_file(), encoding="utf8") as file:
        for line in file.readlines():
            if match := dep_with_version.search(line):
                version_map[match.group(1)] = match.group(2)

    with open("BUILD.reqwest.bazel", "r+", encoding="utf8") as file:

        def repl(m):
            name = m.group(1)
            current_version = m.group(2)
            new_version = version_map.get(name)
            return m.group(0).replace(current_version, new_version)

        data = dep_with_version.sub(repl, file.read())
        file.seek(0)
        file.write(data)


def stage_commit():
    subprocess.run(
        [
            "git",
            "add",
            ".",
            "../Cargo.lock",
            "../rslib/cargo/BUILD.bazel",
            "../pylib/rsbridge/BUILD.bazel",
        ]
    )


update_cargo_lock()
run_cargo_raze()
write_licenses()
update_crates_bzl()
update_reqwest_deps()
stage_commit()
