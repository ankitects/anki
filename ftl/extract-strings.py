#!/usr/bin/env python3
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
Tool to extract core strings and keys from .ftl files.
"""

import glob
import json
import os

from fluent.syntax import parse
from fluent.syntax.ast import Junk, Message
from fluent.syntax.serializer import serialize_element

root = ".."
ftl_files = glob.glob(os.path.join(root, "ftl", "core", "*.ftl"), recursive=True)
keys_by_value: dict[str, list[str]] = {}

for path in ftl_files:
    obj = parse(open(path, encoding="utf8").read(), with_spans=False)
    for ent in obj.body:
        if isinstance(ent, Junk):
            raise Exception(f"file had junk! {path} {ent}")
        if isinstance(ent, Message):
            key = ent.id.name
            val = "".join(serialize_element(elem) for elem in ent.value.elements)
            if val in keys_by_value:
                print("duplicate found:", keys_by_value[val], key)
            keys_by_value.setdefault(val, []).append(key)

json.dump(
    keys_by_value, open(os.path.join(root, "keys_by_value.json"), "w", encoding="utf8")
)
print("keys:", len(keys_by_value))
