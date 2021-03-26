# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import os
import re
from re import Match

import stringcase

TR_REF = re.compile(r"\.tr\(\s*TR::([^,) ]+)\)")


def repl(m: Match) -> str:
    name = stringcase.snakecase(m.group(1))
    # args = m.group(2)
    return f".{name}()"


def update_py(path: str) -> None:
    buf = open(path).read()
    buf2 = TR_REF.sub(repl, buf)
    if buf != buf2:
        open(path, "w").writelines(buf2)
        print("updated", path)
        # print(buf2)


for dirpath, dirnames, fnames in os.walk(os.environ["BUILD_WORKSPACE_DIRECTORY"]):
    if "bazel-" in dirpath:
        continue
    for fname in fnames:
        if fname.endswith(".rs"):
            update_py(os.path.join(dirpath, fname))
