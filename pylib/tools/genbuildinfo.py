# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import sys

version_file = sys.argv[1]
buildhash_file = sys.argv[2]
outpath = sys.argv[3]

with open(version_file, "r", encoding="utf8") as f:
    version = f.read().strip()

with open(buildhash_file, "r", encoding="utf8") as f:
    buildhash = f.read().strip()

with open(outpath, "w", encoding="utf8") as f:
    # if we switch to uppercase we'll need to add legacy aliases
    f.write(f"version = '{version}'\n")
    f.write(f"buildhash = '{buildhash}'\n")
