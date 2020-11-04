#!/usr/bin/env python

import re
import sys

defs_file = sys.argv[1]
stamp_file = sys.argv[2]
release_mode = sys.argv[3] == "release"

version_re = re.compile('anki_version = "(.*)"')

# extract version number from defs.bzl
for line in open(defs_file).readlines():
    if ver := version_re.match(line):
        print(f"STABLE_VERSION {ver.group(1)}")

for line in open(stamp_file).readlines():
    if line.startswith("STABLE_BUILDHASH"):
        if release_mode:
            print(line.strip())
        else:
            # if not in release mode, map buildhash to a consistent value
            print("STABLE_BUILDHASH dev")
