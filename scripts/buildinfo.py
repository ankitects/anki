#!/usr/bin/env python
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import re
import sys

defs_file = sys.argv[1]
stamp_file = sys.argv[2]
release_mode = sys.argv[3] == "release"

version_re = re.compile('anki_version = "(.*)"')


def output(text: str) -> None:
    "Add text with a '\n' to stdout; avoiding a '\r' on Windows"
    sys.stdout.buffer.write(text.encode("utf8") + b"\n")


# extract version number from defs.bzl
for line in open(defs_file).readlines():
    if ver := version_re.match(line):
        output(f"STABLE_VERSION {ver.group(1)}")

for line in open(stamp_file).readlines():
    if line.startswith("STABLE_BUILDHASH"):
        if release_mode:
            output(line.strip())
        else:
            # if not in release mode, map buildhash to a consistent value
            output("STABLE_BUILDHASH dev")
