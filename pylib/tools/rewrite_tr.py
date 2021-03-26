# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import os
import re
from re import Match

import stringcase

TR_REF = re.compile(r"i18n\.tr\(i18n\.TR\.([^,) ]+)\)")


def repl(m: Match) -> str:
    name = stringcase.camelcase(m.group(1).lower())
    #args = m.group(2)
    return f"i18n.{name}()"


def update_py(path: str) -> None:
    buf = open(path).read()
    buf2 = TR_REF.sub(repl, buf)
    if buf != buf2:
        open(path, "w").writelines(buf2)
        print("updated", path)
        #print(buf2)


for dirpath, dirnames, fnames in os.walk(os.environ["BUILD_WORKSPACE_DIRECTORY"]):
    if "bazel-" in dirpath:
        continue
    for fname in fnames:
        if fname.endswith(".ts"):
            update_py(os.path.join(dirpath, fname))
