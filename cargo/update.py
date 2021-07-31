#!/usr/bin/env python3
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
#
# See README.md

# If you get a message like the following during a build:
#
# DEBUG: Rule 'raze__reqwest__0_10_8' indicated that a canonical reproducible
# form can be obtained by modifying arguments shallow_since = "1604362745 +1000"

# ...then the commit and shallow_since argument should be added below, as this
# will remove the debug warning, and speed up the git clone.

COMMITS_SHALLOW_SINCE = {
    # reqwest - must also update crates.bzl reference below
    "7591444614de02b658ddab125efba7b2bb4e2335": "1619519742 +1000",
    # hyper-timeout
    "0cb6f7d14c62819e37cd221736f8b0555e823712": "1619519657 +1000",
    # tokio-io-timeout
    "1ee0892217e9a76bba4bb369ec5fab8854935a3c": "1619517354 +1000",
    # pct-str
    "4adccd8d4a222ab2672350a102f06ae832a0572d": "1605376517 +0100",
    "2f20798ce521cc594d510d4e417e76d5eac04d4b": "1626729019 +0200",
}

import glob
import os
import re
import shutil
import subprocess
import sys

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
                line = line.replace(match.group(0), ":BUILD.reqwest.native")

            output_lines.append(line)

    with open("crates.bzl", "w") as file:
        for line in output_lines:
            file.write(line)
        
        # add rustls version
        file.write("\n".join(" "*4 + l for l in """
maybe(
    new_git_repository,
    name = "reqwest_rustls",
    remote = "https://github.com/ankitects/reqwest.git",
    shallow_since = "1619519742 +1000",
    commit = "7591444614de02b658ddab125efba7b2bb4e2335",
    build_file = Label("//cargo:BUILD.reqwest.rustls.bazel"),
    init_submodules = True,
)
""".splitlines()))

def generated_reqwest_build_file():
    return glob.glob("remote/*reqwest-0.11.3*")[0]


def update_reqwest_deps():
    "Update version numbers in our custom reqwest build files."
    dep_with_version = re.compile(r"@raze__(.+?)__([\d_]+)//")

    version_map = {}
    with open(generated_reqwest_build_file(), encoding="utf8") as file:
        for line in file.readlines():
            if match := dep_with_version.search(line):
                version_map[match.group(1)] = match.group(2)

    for path in "BUILD.reqwest.native.bazel", "BUILD.reqwest.rustls.bazel":
        with open(path, "r+", encoding="utf8") as file:

            def repl(m):
                name = m.group(1)
                current_version = m.group(2)
                new_version = version_map.get(name)
                return m.group(0).replace(current_version, new_version)

            data = dep_with_version.sub(repl, file.read())
            file.seek(0)
            file.write(data)

    with open("remote/BUILD.linkcheck-0.4.1-alpha.0.bazel") as f:
        out = []
        for line in f.readlines():
            line = line.replace("@raze__reqwest__0_11_4//:reqwest","@reqwest_rustls//:reqwest")
            out.append(line)
    with open("remote/BUILD.linkcheck-0.4.1-alpha.0.bazel", "w") as f:
        f.writelines(out)



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
