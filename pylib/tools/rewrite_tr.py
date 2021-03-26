# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import os
import re
from re import Match

import stringcase

TR_REF = re.compile(r"tr\(TR.([^,) ]+),\s*([^)]+)\)")


def repl(m: Match) -> str:
    name = m.group(1).lower()
    args = m.group(2)
    return f"tr.{name}({args})"


def update_py(path: str) -> None:
    buf = []
    changed = False
    for line in open(path):
        line2 = TR_REF.sub(repl, line)
        if line != line2:
            print(line2)
            buf.append(line2)
            changed = True
        else:
            buf.append(line)

    if changed:
        open(path, "w").writelines(buf)
        print("updated", path)


for dirpath, dirnames, fnames in os.walk(os.environ["BUILD_WORKSPACE_DIRECTORY"]):
    if "bazel-" in dirpath:
        continue
    for fname in fnames:
        if fname.endswith(".py"):
            update_py(os.path.join(dirpath, fname))
