#!/usr/bin/env python3
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
Tool to apply transform to an ftl string and its translations.
"""

import glob
import json
import os

from fluent.syntax import parse, serialize
from fluent.syntax.ast import Junk

template_root = os.environ["BUILD_WORKSPACE_DIRECTORY"]
template_files = glob.glob(
    os.path.join(template_root, "ftl", "*", "*.ftl"), recursive=True
)
translation_root = os.path.join(template_root, "..", "anki-i18n")
translation_files = glob.glob(
    os.path.join(translation_root, "*", "*", "*", "*.ftl"), recursive=True
)

target_repls = [
    ["media-recordingtime", "%0.1f", "{ $secs }"],
]


def transform_string_in_file(path):
    obj = parse(open(path).read(), with_spans=False)
    changed = False
    for ent in obj.body:
        if isinstance(ent, Junk):
            raise Exception(f"file had junk! {path} {ent}")
        if getattr(ent, "id", None):
            key = ent.id.name
            for (target_key, src, dst) in target_repls:
                if key == target_key:
                    for elem in ent.value.elements:
                        newval = elem.value.replace(src, dst)
                        if newval != elem.value:
                            elem.value = newval
                            changed = True

    if changed:
        open(path, "w", encoding="utf8").write(serialize(obj))
        print("updated", path)


for path in template_files + translation_files:
    transform_string_in_file(path)
