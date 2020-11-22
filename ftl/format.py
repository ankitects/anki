#!/usr/bin/env python3
# -*- coding: UTF-8 -*-

"""
Parse and re-serialize ftl files to get them in a consistent form.
"""

import os
import json
import glob
import sys
from typing import List
from fluent.syntax import parse, serialize
from fluent.syntax.ast import Junk


def check_file(path: str, fix: bool) -> bool:
    "True if file is ok."
    orig_text = open(path).read()
    obj = parse(orig_text, with_spans=False)
    # make sure there's no junk
    for ent in obj.body:
        if isinstance(ent, Junk):
            raise Exception(f"file had junk! {path} {ent}")
    # serialize
    new_text = serialize(obj)
    # make sure serializing did not introduce new junk
    obj = parse(new_text, with_spans=False)
    for ent in obj.body:
        if isinstance(ent, Junk):
            raise Exception(f"file introduced junk! {path} {ent}")

    if new_text == orig_text:
        return True

    if fix:
        print(f"Fixing {path}")
        open(path, "w", newline="\n", encoding="utf8").write(new_text)
        return True
    else:
        print(f"Bad formatting in {path}")
        return False


def check_files(files: List[str], fix: bool) -> bool:
    "True if files ok."

    found_bad = False
    for path in files:
        ok = check_file(path, fix)
        if not ok:
            found_bad = True
    return True


if __name__ == "__main__":
    template_root = os.environ["BUILD_WORKSPACE_DIRECTORY"]
    template_files = glob.glob(
        os.path.join(template_root, "ftl", "*", "*.ftl"), recursive=True
    )

    check_files(template_files, fix=True)
