# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import os
import re
from re import Match

import stringcase

TR_REF = re.compile(
    r'\.trn\(\s*TR::([^,) ]+),\s+tr_(?:strs|args)!\[((?:"[A-z0-9_]+"\s*=>[^=]+?)+)]\s*,?\s*\)'
)
TR_ARG_INNER = re.compile(r'"([A-z0-9_]+)"\s*=>(?!=>)+,?\s*')

# eg "count"=>output.trash_count, "megs"=>megs
def rewrite_tr_args(args: str) -> str:
    return TR_ARG_INNER.sub("", args)


def repl(m: Match) -> str:
    name = stringcase.snakecase(m.group(1))
    args = rewrite_tr_args(m.group(2))
    # print(m.group(0))
    # print(f".{name}({args})")
    # print(args)
    return f".{name}({args})"


def update_py(path: str) -> None:
    buf = open(path).read()
    buf2 = TR_REF.sub(repl, buf)
    if buf != buf2:
        open(path, "w").writelines(buf2)
        print("updated", path)
        #  print(buf2)


for dirpath, dirnames, fnames in os.walk(os.environ["BUILD_WORKSPACE_DIRECTORY"]):
    if "bazel-" in dirpath:
        continue
    for fname in fnames:
        if fname.endswith(".rs"):
            update_py(os.path.join(dirpath, fname))
