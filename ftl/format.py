#!/usr/bin/env python3
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
Parse and re-serialize ftl files to get them in a consistent form.
"""

import difflib
import glob
import json
import os
import sys
from typing import List

from compare_locales import parser
from compare_locales.checks.fluent import ReferenceMessageVisitor
from compare_locales.paths import File
from fluent.syntax import parse, serialize
from fluent.syntax.ast import Junk


def check_missing_terms(path: str) -> bool:
    "True if file is ok."
    file = File(path, os.path.basename(path))
    content = open(path, "rb").read()
    p = parser.getParser(file.file)
    p.readContents(content)
    refList = p.parse()

    p.readContents(content)
    for e in p.parse():
        ref_data = ReferenceMessageVisitor()
        ref_data.visit(e.entry)

        for attr_or_val, refs in ref_data.entry_refs.items():
            for ref, ref_type in refs.items():
                if ref not in refList:
                    print(f"In {path}:{e}, missing '{ref}'")
                    return False

    return True


def check_file(path: str, fix: bool) -> bool:
    "True if file is ok."
    orig_text = open(path, encoding="utf8").read()
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
        return check_missing_terms(path)

    if fix:
        print(f"Fixing {path}")
        open(path, "w", newline="\n", encoding="utf8").write(new_text)
        return True
    else:
        print(f"Bad formatting in {path}")
        print(
            "\n".join(
                difflib.unified_diff(
                    orig_text.splitlines(),
                    new_text.splitlines(),
                    fromfile="bad",
                    tofile="good",
                    lineterm="",
                )
            )
        )
        return False


def check_files(files: List[str], fix: bool) -> bool:
    "True if files ok."

    found_bad = False
    for path in files:
        ok = check_file(path, fix)
        if not ok:
            found_bad = True
    return not found_bad


if __name__ == "__main__":
    template_root = os.environ["BUILD_WORKSPACE_DIRECTORY"]
    template_files = glob.glob(
        os.path.join(template_root, "ftl", "*", "*.ftl"), recursive=True
    )

    check_files(template_files, fix=True)
